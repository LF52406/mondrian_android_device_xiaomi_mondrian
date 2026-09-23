# Mondrian display settings

System integration for **Settings → Display → Screen resolution** on POCO F5 Pro
(`mondrian`). The module owns the logical FHD+/WQHD+ selector and the user-facing
control for the existing M11A Partial Update path.

| Choice | Logical Android size | Native panel timing |
| --- | --- | --- |
| FHD+ | 1080 × 2400 | 1440 × 3200 |
| WQHD+ | 1440 × 3200 | 1440 × 3200 |

Changing resolution uses WindowManager logical size and density. It does not add a
new physical display mode or change the panel's native timing.

## UI

The page uses a custom Settings-themed layout rather than the old preference list.

- FHD+ and WQHD+ are full-card radio targets.
- The selected card is derived from the actual WindowManager size.
- Card presses use a short scale animation without consuming the normal click or
  accessibility path.
- A successful resolution transaction is confirmed immediately. There is no
  user-facing 20-second confirmation dialog.
- The existing transaction journal, exact rollback alarm and recovery paths remain
  in place for interrupted or failed changes.
- The Partial Update illustration is informational only. Its only control is the
  standard Switch beside the section title.
- English and Russian strings are Android resources.

The supplied landscape artwork is packaged as
`res/drawable-nodpi/resolution_landscape_preview.webp`. WQHD+ draws it normally;
FHD+ uses a deliberately pixelated preview to communicate the visual difference.

## Resolution backend and recovery

Before a real display mutation, the previous size, scaling mode and full-user
densities are stored in the existing atomic journal. Size and density are still
applied through the existing typed WindowManager Binder APIs.

The new UI runs `preview()` and `confirm()` consecutively on the controller's
single worker. Density-driven Activity recreation does not expose an intermediate
confirmation UI. If the process is interrupted before confirmation, the durable
journal/alarm/boot recovery path restores the previous state.

Integer density anchors and multi-user density preservation remain unchanged.
Only the unlocked foreground owner can change the shared logical display size.

The required SettingsLib companion patch is also unchanged. It keeps the FHD+
logical default density proportional to the native WQHD+ density, so Display size
and Reset do not change perceived UI scale.

Apply it before building:

```bash
bash device/xiaomi/mondrian/display-settings/apply-settingslib-patch.sh
m Settings MondrianDisplaySettings
```

## M11A Partial Update

The UI uses the already existing kernel implementation:

```text
/sys/module/msm_drm/parameters/m11a_partial_update_profile
0 = disabled / full-frame
1 = SAFE full-width DSC-aligned ROI
2 = DSC-slice development profile
```

The user-facing Switch exposes only profiles 0 and 1. Profile 2 is not selectable
from Settings.

Partial Update is intentionally available only at WQHD+ (1440 × 3200):

- at WQHD+, Switch ON requests SAFE profile 1 and Switch OFF requests profile 0;
- at FHD+, the runtime profile is forced to 0 and the Switch is disabled/off;
- the user's WQHD+ preference is stored separately, so returning from FHD+ restores
  the previous ON/OFF preference when possible.

The app does **not** write the root-owned module parameter directly. It writes only
the dedicated Mondrian system properties. `init.mondrian.display.rc` performs the
sysfs write, and the app reads the real module parameter back before reporting a
successful Switch change. Device-specific SELinux labels restrict this bridge to
the dedicated property and sysfs node.

A failure to disable Partial Update before entering FHD+ aborts that resolution
change. After a resolution has already committed, a failure to restore the optional
WQHD+ Partial Update preference does not falsely report that the resolution change
failed; the UI displays the actual sysfs state and later resume/boot reconciliation
can retry it.

## Search and security

The Settings entry, search provider and dynamic resolution summary remain in the
same `MondrianDisplaySettings` system_ext app. The Activity remains signature
protected. The exported search provider is read-only and protected by
`READ_SEARCH_INDEXABLES`.

The app requires no root, shell command execution, network access or shared system
UID. Hardware mutation for Partial Update is delegated to init through the
device-specific property bridge.

## Verification

The existing Android-independent transaction tests are still applicable:

```bash
bash device/xiaomi/mondrian/display-settings/tests/run-host-tests.sh
```

A full Soong build and physical-device validation are still required for the custom
Android UI, init/property bridge and SELinux policy.

Recommended device checks:

1. Build `Settings` and `MondrianDisplaySettings`; verify the Display entry and
   Settings search in Russian and English, light/dark theme and larger font sizes.
2. Switch WQHD+ → FHD+ and FHD+ → WQHD+ by tapping anywhere on each card. Confirm
   there is no confirmation dialog and the selected border/radio follows `wm size`.
3. In FHD+, verify the module parameter is 0 and the Partial Update Switch is
   disabled/off. Return to WQHD+ and verify the stored user preference is restored.
4. In WQHD+, toggle Partial Update both ways and verify the Switch only changes after
   the module parameter reaches the requested 0/1 value.
5. Kill/restart Settings and reboot in both logical resolutions. Re-open the page and
   verify both resolution and Partial Update are reconstructed from actual system
   state rather than stale View state.
6. Test owner/secondary/guest handling, Display size/reset, rotation, UDFPS,
   AOD/LHBM, camera, screenshots, recording and 60/90/120 Hz independently.

Read-only diagnostics:

```bash
adb shell wm size
adb shell wm density
adb shell cat /sys/module/msm_drm/parameters/m11a_partial_update_profile
adb shell getprop persist.sys.mondrian.partial_update_enabled
adb logcat -d -s MondrianResolution MondrianPartialUpdate WindowManager AndroidRuntime
```

Manual resolution recovery remains:

```bash
adb shell wm size reset
adb shell wm density reset
adb shell wm scaling auto
```
