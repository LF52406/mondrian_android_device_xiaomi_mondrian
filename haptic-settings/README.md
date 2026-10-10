# Mondrian Haptic Engine

Status: initial implementation for Android 17. Full ROM compilation and on-device
validation are pending. The recipe amplitudes and the percentage curve are initial
values, not measurements or a completed physical calibration.

## Integration

`device.mk` inherits `haptic-settings/product.mk`. During product configuration it
runs the idempotent framework preparation script, then installs
`MondrianHapticSettings` and `MondrianHapticEngineOverlay`. No changes to a ROM's
Settings application are required when it retains AOSP external dashboard tiles.
The Activity registers in the Sound category via `IA_SETTINGS`.

The patch lives in this device tree; it does not require a separate MistOS or
Evolution X framework fork. Preparation changes the framework working tree, not
its Git history. Repeating it verifies the existing patch. Conflicts and partial
application stop product configuration with an error rather than silently omit
the backend. The script uses a checkout-local lock for concurrent product queries.
It also recognizes complete v1 (`ae6abe0` / `fb0e202`) and v2 (`ee35d58`, including
subsequent settings-only updates) installations and applies their checked direct
upgrade to the current integration. Re-running with the current integration
is a no-op. An unrecognized or conflicting partial installation fails before any
write; the script never resets framework files or discards local changes.

For explicit preparation from the ROM root:

```sh
python3 device/xiaomi/mondrian/haptic-settings/apply-framework-patch.py --rom-root .
```

To omit the feature, set `MONDRIAN_HAPTIC_ENGINE := false` before inheriting
`device.mk`. This omits both APK and overlay. An already-applied framework patch
remains dormant without its device opt-in. It is not automatically reversed.
Unknown framework revisions may need an adapter; arbitrary ROM compatibility is
not guaranteed by the package name alone.

## Interface references

The interface follows the approved `651.png` main screen and `642.png` profile
screen: a compact central waveform on the main page, a wider multi-peak waveform
on the profile page, separate profile artwork in rounded tiles, outlined selection
and the checked switch thumb. The preview buttons use the corresponding artwork
and proportions from each screen.

The continuous 0–100 slider has eight visual guide dots, a rounded track and a
large circular thumb. The dots do not quantize its value. The switch retains
Android's native dragging, thumb animation, keyboard and accessibility behavior.
Typography, card spacing and corner sizes follow the references in density-aware
units; text can wrap at larger font sizes. Profile icons compact on narrow screens
or larger font scales to preserve text space. Colors come from Monet in both
light and dark themes; the reference's lavender is not hardcoded.

The updated UI resources link with AAPT2, and all current settings Java sources
compile against Android 17 classes. An actual installed-screen comparison on
mondrian remains pending; no rendered device screenshot is claimed.

## Behavior

- The engine is initially OFF. Turning it on starts with Balanced at 60%.
- Soft, Balanced and Crisp use different primitive choices and compositions.
- Direct Launcher3 Recents scrolling is calibrated separately on mondrian: Mist Launcher
  emits a single LOW_TICK at scale 0.6 for card paging/quick-switch, and the Crisp
  profile maps only that exact `com.android.launcher3` signature to CLICK because the
  device's TICK primitive is substantially less perceptible there. Pixel Launcher
  (`com.google.android.apps.nexuslauncher`) keeps the generic direct-haptic mapping.
- The slider stores every integer from 0 to 100. The visual dots are guides, not
  three discrete levels. The provisional gain is `(percent / 100)^1.35`.
- 0 mutes engine-owned events; 100 reaches the recipe's configured maximum.
  These are software scales, not a claim of 100 perceptually distinct steps.
- Engine-owned compositions skip AOSP's coarse intensity multiplier, preventing
  double scaling. Adaptive scaling remains at its normal stage. No public bypass
  flag is introduced: only the internal haptic path marks owned vibrations.
- Android's system OFF and existing policy retain priority. The implementation
  does not change the stock intensity settings. The percentage is the sole strength
  control for covered events; LOW/MEDIUM/HIGH continue to control stock paths.
  There is deliberately no three-level-to-percentage conversion or bidirectional
  observer loop: stock touch intensity also affects accessibility and, on some
  configurations, IME. Moving this slider must not change those effects.
  Stock touch OFF, the legacy touch-feedback switch and the global vibration
  switch all suppress engine events. Hardware-feedback OFF additionally suppresses
  its covered hardware events. Restoring a stock switch restores the saved engine
  percentage. A slider value of 0 mutes only covered engine events.
- One versioned Secure setting, `mondrian_haptic_engine`, stores
  `1:enabled:profile:strength` per user. Writes occur off the UI thread. Framework
  observers cache it, reload on user switch, and do not read storage for each
  ordinary haptic. Reset restores only this setting's defaults.
  One process-wide writer preserves save order across Activity recreation; callbacks
  from a previous screen lifecycle cannot overwrite a resumed screen's state.
  Controls wait for a fresh settings snapshot on resume. Explicit preview requests
  flush a pending slider value and wait for queued writes. A failed write disables
  editing until a single reload restores the stored value; a failed read does not
  trigger a retry loop. Leaving and reopening the screen retries loading.
- Previews resolve the same recipes in system_server, after refreshing the saved
  configuration. Private IDs 20001–20003 require `VIBRATE_SYSTEM_CONSTANTS`.
- Turning the engine off returns to the existing provider. Muting/disabling also
  cancels its active/queued samples, without cancelling unrelated vibrations.
  System OFF and input-device routing changes use the same cancellation rule.
  Engine admission is checked under the service lock before interrupting the
  current session or replacing a queued session. A stale suppressed request cannot
  cancel another session before being rejected. The cached policy is checked again
  when a queued sample starts. Owned compositions are never forwarded to an
  external input device, including if the delegate changes after admission.

## Coverage and HAL

Covered: virtual-key feedback, context click, confirmation, gesture start/end and
thresholds, clocks and segment ticks, scroll ticks/focus/limit, toggles, long press,
drag start/crossing. Input-specific mapping is used only for the phone touchscreen.
Virtual devices and enabled input-device vibration routing retain stock behavior.

Text-handle feedback is enabled only while the engine is on, via its own device
resource. It does not change `config_enableHapticTextHandle` for the stock path.

IME, biometric/reject effects, power and safe-mode feedback, alarms, calls,
notifications, media, accessibility, direct application `vibrate()` calls and
external control are not globally replaced by this engine.

The device's existing QTI AIDL HAL, `libqtivibratoreffect.xiaomi`, HyperOS RTP files,
kernel and persist calibration remain unchanged. Recipes use supported CLICK,
TICK, LOW_TICK, THUD, QUICK_RISE and QUICK_FALL primitives. Missing support or a
composition/delay limit returns to the original provider, whose predefined
effects may have only discrete strengths. No fake PWLE/frequency controls exist.

## Source checkpoints

Device base: `9d9ebe22614fc6c453ca3e2fc3b27bfe2167a0a8` on
`LF52406/mondrian_android_device_xiaomi_mondrian:lineage-24.0`.

Framework source and patch base:
`Project-Mist-OS/frameworks_base_qpr2:17.0`,
`32fd66882b396a287402596219a4d69fd90d320e`.

The vibrator package also matches
`Evolution-X/frameworks_base:cnb` at
`d5d4e643234708b7a3c280e7d41ba7fd385aaef1`.

Settings registration was inspected in MistOS
`c29acb193e62ce3081960f7dc3e270c1f56af2a2` and Evolution X
`36d32b115a6ce46e8b3098b62ccd1fe5a1f80fc0`.

## Validation status and next work

The application's resources were compiled/linked using AAPT2. The new config,
recipe engine and application Java in the initial implementation were checked
against an Android 17 framework jar. These checks predate the follow-up lifecycle
and queued-policy changes. This is not a Soong platform build or an installed APK test. Compiling the
entire service against that standalone jar is blocked by missing generated AOSP
Flags and R8 annotations; those inputs belong to the ROM build.

Additional compilation/test setup was stopped at the user's request to conserve quota.
No full ROM build, instrumentation run or physical haptic calibration is claimed.

The follow-up integration check used the exact affected files from the two
framework revisions above: the current patch passes a clean apply check on both;
initial installation on MistOS and upgrade from v1 on Evolution X produce the
expected file contents. Repeated verification recognizes the completed patch.
This checks patch integration only, not the complete product build hook or runtime.

The admission-order fix also passes focused integration checks for clean setup,
direct upgrades from v1 and v2, repeated application and check-only mode. A local
edit outside the patch hunks is preserved; conflicting and incomplete installations
are rejected without modifying source files. Runtime race reproduction and a full
platform build remain pending.

Next: build the APK and services in a complete Android 17 checkout; verify the
product-configuration preparation hook in that build, Settings tile discovery,
per-user persistence and permissions, real UI layout/accessibility, system OFF,
all profile/strength boundaries and the physical feel on mondrian. A source-level
implementation and standalone compilation do not replace these checks.
