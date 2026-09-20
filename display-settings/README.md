# Mondrian screen resolution

The companion [M11A Partial Update controls](partial-update/README.md) require
their matching kernel changes plus the device-carried HWC/SDM patch. No separate
display repository is required. The resolution engine itself is unchanged.

System integration for **Settings → Display → Screen resolution** on POCO F5 Pro
(`mondrian`), with English and Russian strings, Settings search, and a live summary.

| Choice | Logical Android size | Native panel timing |
| --- | --- | --- |
| FHD+ | 1080 × 2400 | 1440 × 3200 |
| WQHD+ | 1440 × 3200 | 1440 × 3200 |

This changes WindowManager's logical size and density. It does not advertise an
additional hardware display mode or alter DSI, DSC, refresh-rate settings, touch
drivers, UDFPS coordinates, or LHBM commands. Apps can choose their own rendering
buffers; reduced logical pixel count is not a measured battery-life improvement.

## Build integration

The app is included by `device/xiaomi/mondrian/device.mk`, and installs into
`system_ext` with its permission allowlist. It uses the same SettingsLib theme and
selector widgets as the current A17 Settings. No launcher icon is installed.

**Apply the companion SettingsLib patch before building.** Without it, AOSP's
Display size page uses the native 560 dpi baseline even in FHD+, and its reset
changes the physical size of the UI. The patch makes the default 420 dpi in FHD+
when the native density is 560; the actual calculation uses the device's default
density. It is limited to mondrian's default display and these exact dimensions.
Other devices, external displays, and native WQHD+ keep their existing behavior.

The app calls the public density getter introduced by this patch, so an unpatched
SettingsLib causes a compilation error instead of silently shipping inconsistent
display-size controls.

From the existing ROM source root, after selecting the normal mondrian build target:

```bash
bash device/xiaomi/mondrian/display-settings/apply-settingslib-patch.sh
bash device/xiaomi/mondrian/display-settings/apply-partial-update-display-patch.sh
m Settings MondrianDisplaySettings
```

Then build and install the ROM using the existing build target and flashing workflow.
Both Settings and the app must come from this patched build. Building or installing
only the app does not update the Settings app's statically linked SettingsLib.

The patch script is repeatable. It checks before applying, detects an already
applied patch, and refuses to modify a density source file with other local edits.
It does not sync repositories, reset branches, overwrite other framework changes,
or make a commit in the ROM checkout. Keep the patch applied when updating sources;
review it again if an upstream update no longer accepts it cleanly.

Reference sources inspected:

| Project | Source revision |
| --- | --- |
| device tree | `LF52406/mondrian_android_device_xiaomi_mondrian`, `lineage-24.0`, `b1a2111` |
| framework / SettingsLib | `ascp/platform_frameworks_base`, `17-codeberg`, `96ce5f79dbb4940e7ebbaa9ab49408058c6ad052` |
| Settings | `ascp-lfs/platform_packages_apps_Settings`, `17-codeberg`, `1fb20426dd23c818602aa6e112d8c3438da82040` |

This patch targets the A17 source in the current manifest. It does not use the
unrelated Android 16 `frameworks_base:bq2` repository.

## State and recovery

- Before the first display mutation, the old size, scaling mode, and full-user
  densities are written into an atomic journal in device-protected storage, and
  an exact rollback alarm is armed.
- The two Binder operations for size and density are separate platform calls.
  They are serialized and checked as a recoverable transaction; they are not an
  atomic WindowManager API. A short reconfiguration or app recreation can occur.
- The user has 20 seconds to keep the change. Back, leaving the page, timeout,
  user switching, an interrupted preview followed by boot, or a failed operation
  trigger restoration. Density-related activity recreation retains the preview.
- Pending state is independent of the Activity/process. Failed restoration keeps
  its journal and schedules another attempt; boot recovery also checks it.
- Confirmed size and density are persisted by WindowManager. There is no boot
  service repeatedly overriding a confirmed resolution.
- Integer density anchors are retained across confirmed switches to avoid
  cumulative rounding drift. A new Display size/developer density setting
  replaces the anchor when its actual value differs from the last applied one.
- Only the unlocked foreground owner (user 0) can change the shared size. Full
  users' individual scales are preserved; managed profiles do not independently
  own the physical display. Newly created full users receive a proportional
  default density via user/boot handling when the device is in FHD+.
- The entry uses a signature-protected Activity. The exported provider exposes
  only search data and a read-only summary, protected by READ_SEARCH_INDEXABLES.
  The resolution path needs no root, network, shell execution or shared system UID.
  Partial Update adds a dedicated SELinux property read by the composer.

## Verification

Run the Android-independent regression suite with a JDK 17 or newer:

```bash
bash device/xiaomi/mondrian/display-settings/tests/run-host-tests.sh
```

The suite compiles the production transaction engine and journal codec. It covers
confirmation, timeout, process recreation, reboot, stale actions, partial size /
density failure, rollback retry, journal/alarm failures, custom size restoration,
independent user densities, externally changed density, and repeated switching.
These tests do not compile the Android UI or exercise WindowManager on a phone.

The SettingsLib patch and its repeatable apply script are checked against the
source revision above. A full Soong build and device testing are still required.

### Device acceptance checks

1. Build `Settings` and `MondrianDisplaySettings` successfully, then install the
   resulting ROM. Confirm one resolution entry under Display and in Settings
   search; verify Russian/English, light/dark theme and large font settings.
2. Start at WQHD+. Select FHD+ and keep it. With default screen size, `wm size`
   should report an override of `1080x2400` and `wm density` an override of `420`
   for a native default of `560`. The summary and selected radio must match.
3. Open Display size / Display size and text. Test its slider and Reset in FHD+;
   the default should remain `420` rather than `560`. Return to WQHD+ and confirm
   the same perceived scale. Repeat with a custom starting density such as `562`.
4. Test both directions: let the confirmation expire, press Revert, press Back,
   go Home, rotate, and change font/locale. Verify the prior size and density
   return on cancellation, and rotation retains a live preview.
5. With a preview pending, terminate the process without force-stopping the
   package. Its alarm must restore the previous values. Reboot during another
   preview and check restoration before unlocking. Reboot after confirmation and
   check that the selected resolution remains.
6. Test owner/secondary/guest switching with distinct display-size preferences,
   and creation of a new full user while in FHD+. A secondary user cannot start
   a change. Check a work profile alongside its parent user.
7. In both confirmed resolutions, test UDFPS enrollment and unlock, AOD, the
   fingerprint circle position and release, rotation, camera, screenshots,
   screen recording and full-screen apps. Test 60/90/120 Hz independently.

The inspected A17 `UdfpsUtils.getScaleFactor()` already derives its scale from
logical dimensions versus the maximum physical mode. That is source evidence
for coordinate scaling, not a substitute for testing sensor/panel behavior.

Read-only diagnostics:

```bash
adb shell wm size
adb shell wm density
adb shell dumpsys display
adb logcat -d -s MondrianResolution WindowManager AndroidRuntime
```

For manual recovery to the device's native resolution and default UI scale:

```bash
adb shell wm size reset
adb shell wm density reset
adb shell wm scaling auto
```

The last density command resets that user's custom Display size as well. If a
preview is still pending, let it revert first or use the on-screen Revert button.
