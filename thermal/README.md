# Mondrian Android 17 thermal migration — stage 1

Optional test tooling for POCO F5 Pro / Redmi K60 (mondrian, SM8475, SoC ID 530).
It is NOT invoked by device.mk. No daemon, kernel cooling, charging limits,
Power HAL or XiaomiParts settings are altered by adding these files.

## Evidence from the device

Two October 20, 2026 captures confirm that QTI AIDL Thermal HAL v3 and
mi_thermald were both running. The QTI HAL published CPU/GPU/BATTERY/BCL
temperatures, but not SKIN.

The active kernel exposed quiet_therm (thermal_zone74): 33.38–36.39 °C
during normal use and 36.64–41.23 °C in the game capture. The stock
QTI waipio_specific SKIN configuration uses xo-therm; that zone was
not present in the device capture. Quiet_therm is a plausible candidate,
NOT YET a calibrated outer-enclosure temperature sensor.

## Opt-in AIDL Thermal HAL test patch

Run in a checked-out Android 17 source tree that contains the QTI HAL
at hardware/qcom/thermal/thermalConfig.cpp:

    python3 device/xiaomi/mondrian/thermal/apply_thermal_hal_patch.py --check --tree "$PWD"
    python3 -m unittest discover -s device/xiaomi/mondrian/thermal/tests -v
    python3 device/xiaomi/mondrian/thermal/apply_thermal_hal_patch.py --apply --tree "$PWD"
    git -C hardware/qcom/thermal diff --check
    m android.hardware.thermal-service.qti

The script modifies the QTI source only after --apply is explicitly
requested. It applies a runtime name correction ONLY if SoC ID is 530
AND ro.product.device is mondrian. It does not affect other devices.
The existing Waipio SKIN thresholds are unchanged.

WARNING: Validate quiet_therm against an actual measured enclosure
temperature in idle, gaming, charging and warm ambient conditions before
shipping. Existing Waipio SKIN thresholds might be wrong for this sensor.
This change does not replace mi_thermald or its thermal mitigation logic.

After flashing a test build, check the thermal HAL logs and run
dumpsys thermalservice. SKIN must become visible with plausible readings,
without HAL startup errors. Correlate framework thermal severity with
a physical temperature probe.

To revert only this patch and preserve unrelated HAL edits:

    python3 device/xiaomi/mondrian/thermal/apply_thermal_hal_patch.py --revert --tree "$PWD"

## Required before retiring Xiaomi thermal

1. Determine actual CPU/GPU limit owners (Power HAL, LMH, MIUI) through
   controlled measurements, not assumptions.
2. Implement and verify fail-safe mitigation for SoC, battery, USB,
   charging, modem, display and skin; retain kernel/PMIC emergency limits.
3. Keep powerhint.json, task_profiles.json, cgroups.json and ADPF intact.
4. Migrate the per-app XiaomiParts profile backend without breaking the
   game network-latency controller sharing ThermalService.
5. Validate boot and charger paths, SELinux/VINTF, sustained FPS, energy
   and temperatures before removing mi_thermald.
