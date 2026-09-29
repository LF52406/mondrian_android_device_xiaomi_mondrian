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

For explicit preparation from the ROM root:

```sh
python3 device/xiaomi/mondrian/haptic-settings/apply-framework-patch.py --rom-root .
```

To omit the feature, set `MONDRIAN_HAPTIC_ENGINE := false` before inheriting
`device.mk`. This omits both APK and overlay. An already-applied framework patch
remains dormant without its device opt-in. It is not automatically reversed.
Unknown framework revisions may need an adapter; arbitrary ROM compatibility is
not guaranteed by the package name alone.

## Behavior

- The engine is initially OFF. Turning it on starts with Balanced at 60%.
- Soft, Balanced and Crisp use different primitive choices and compositions.
- The slider stores every integer from 0 to 100. The visual dots are guides, not
  three discrete levels. The provisional gain is `(percent / 100)^1.35`.
- 0 mutes engine-owned events; 100 reaches the recipe's configured maximum.
  These are software scales, not a claim of 100 perceptually distinct steps.
- Engine-owned compositions skip AOSP's coarse intensity multiplier, preventing
  double scaling. Adaptive scaling remains at its normal stage. No public bypass
  flag is introduced: only the internal haptic path marks owned vibrations.
- Android's system OFF and existing policy retain priority. The implementation
  does not change the stock intensity settings. While enabled, engine percentages
  control covered events; LOW/MEDIUM/HIGH continue to control stock paths.
  A shared bidirectional percentage/stock-level UI model is not implemented.
- One versioned Secure setting, `mondrian_haptic_engine`, stores
  `1:enabled:profile:strength` per user. Writes occur off the UI thread. Framework
  observers cache it, reload on user switch, and do not read storage for each
  ordinary haptic. Reset restores only this setting's defaults.
- Previews resolve the same recipes in system_server, after refreshing the saved
  configuration. Private IDs 20001–20003 require `VIBRATE_SYSTEM_CONSTANTS`.
- Turning the engine off returns to the existing provider. Muting/disabling also
  cancels its active/queued samples, without cancelling unrelated vibrations.

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
recipe engine and application Java are checked against an Android 17 framework
jar. This is not a Soong platform build or an installed APK test. Compiling the
entire service against that standalone jar is blocked by missing generated AOSP
Flags and R8 annotations; those inputs belong to the ROM build.

Additional test work was stopped at the user's request to conserve quota.
No full ROM build, instrumentation run or physical haptic calibration is claimed.

Next: build the APK and services in a complete Android 17 checkout; verify the
product-configuration preparation hook in that build, Settings tile discovery,
per-user persistence and permissions, real UI layout/accessibility, system OFF,
all profile/strength boundaries and the physical feel on mondrian. A source-level
implementation and standalone compilation do not replace these checks.
