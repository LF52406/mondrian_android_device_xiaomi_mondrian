# Mondrian display settings

Android 17, POCO F5 Pro / M11A. The two existing cards switch the logical display
between 1080×2400 and 1440×3200; physical panel timing remains 1440×3200.

A tap applies and saves immediately. There is no preview, confirmation dialog or
20-second revert. The atomic MRS1 journal, rational density anchors and full-user
densities are preserved. The recovery alarm covers **only an unfinished operation**;
a completed journal cannot be rolled back by a stale alarm. Existing pending preview
journals are recovered once through the same guarded transition.

Partial Update is opt-in and available only in WQHD+. FHD+ uses profile 0 and keeps
the WQHD+ preference for the return transition. An absent preference means OFF.
The Switch shows kernel state; text and the neutral diagram distinguish an
unverified state from an acknowledged OFF. Cards, artwork and card press animation
are retained, with flexible text height and a full-width explanatory SAFE band.

## Required integration

Apply the matching kernel change and both companion patches before building:

```bash
bash device/xiaomi/mondrian/display-settings/apply-display-patches.sh "$ANDROID_BUILD_TOP"
m Settings MondrianDisplaySettings vendor.qti.hardware.display.composer-service
```

An alternative HWC checkout can be passed as the second argument. Both patches
are checked before either is applied. The script also supports upgrading the old
SettingsLib patch and is idempotent. It does not apply the BootAnimation patch.
The app requires `mondrian_display_hwc_integration`, supplied by the HWC companion;
a build with an unpatched HWC fails instead of silently using the old sysfs bridge.

HWC patch baseline: LineageOS `android_hardware_qcom_display`,
`lineage-24.0-caf-sm8450`, `aea8d60119598d755f29e2e15312b4ff491d1808`.
SettingsLib baseline: LineageOS `android_frameworks_base`, `lineage-24.0`,
`d00cd79b907ed2df8049eb57b5da540bd8b91d04`.
These are integration baselines, not an assertion about a particular ROM manifest.

## Transition contract

1. Persist the before/after journal and arm process-death recovery.
2. Request a fresh HWC full composition. Invalidate its old validation and wait for
   a real non-null retire fence, then ask init to write kernel profile 0.
3. Apply WM size, scaling and densities. Flush scheduled WM placement and request
   another full composition and retire fence. Check WM state and foreground owner.
4. Commit the journal. Only then reconcile the saved WQHD+ PU preference.

Apply, rollback and boot recovery share `ResolutionTransition`. Only the owner
process can mutate the shared display. UI writes also require the unlocked
foreground owner; secondary-user refreshes are read-only.

The generation-tagged handshake is app → init → HWC → init/sysfs. HWC disables its
PU composition policy before requesting kernel OFF; kernel ROI expansion alone
cannot reconstruct cropped planes. Enabling programs profile 1 first, then permits
PU in HWC. Normal Qualcomm full-frame fallbacks remain in force. Every request
requires its own fence and init acknowledgement. A timed-out init action must be
drained before another kernel write is queued, preventing a late ON after OFF.
HWC restart closes its PU gate and invalidates acknowledgement; it does not replay
an old ON request. Reconciliation is retried on the next owner recovery/resume.

Kernel starts at profile 0. Capabilities remain stable, SAFE alignment remains
1440×32, DSC remains 720×32. Full-frame policy is resolved before LM/DSC/DSI and
marks ROI dirty when retained rectangles need reprogramming. Profile 2 remains a
development-only kernel setting and is never offered in Settings.

## Checks

```bash
bash device/xiaomi/mondrian/display-settings/tests/run-host-tests.sh
```

Host tests cover immediate persistence, stale alarms, legacy journals, density
rounding, owner loss, both directions, guard/fence failure, partial WM failure,
process death and journal/rollback failures. They do not replace a target Soong,
SELinux or kernel build, nor M11A validation.

On device, test all four resolution/preference combinations, reboot and process
interruption. Correlate HWC composition, plane rectangles, LM, DSC, DSI and retire
fences: acknowledged OFF must transmit full native 1440×3200, including logical
FHD. ON may use full-width bands aligned to 32 lines or normal full-frame fallback.
WM readback and profile alone do not prove that hardware contract. Check RU/EN,
large fonts, selected states and accessibility on the actual Settings theme.

Read-only state:

```bash
adb shell wm size
adb shell wm density
adb shell cat /sys/module/msm_drm/parameters/m11a_partial_update_profile
adb shell getprop sys.mondrian.partial_update_status
adb shell getprop persist.sys.mondrian.partial_update_enabled
```
