#!/usr/bin/env bash
# Copyright (C) 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
rom_root="${1:-${ANDROID_BUILD_TOP:-$PWD}}"
display_dir="$rom_root/hardware/qcom-caf/sm8450/display"
patch_file="$script_dir/patches/0003-M11A-partial-update-HWC-SDM.patch"
panel_dtsi="$rom_root/kernel/xiaomi/sm8450-devicetrees/qcom/display/display/dsi-panel-m11a-42-02-0a-dsc-cmd.dtsi"
dsi_display="$rom_root/kernel/xiaomi/sm8450-modules/qcom/opensource/display-drivers/msm/dsi/dsi_display.c"
sde_crtc="$rom_root/kernel/xiaomi/sm8450-modules/qcom/opensource/display-drivers/msm/sde/sde_crtc.c"

required=(
    "sdm/libs/core/display_base.cpp"
    "sdm/libs/core/display_base.h"
    "sdm/libs/core/drm/hw_device_drm.cpp"
    "sdm/libs/core/drm/hw_peripheral_drm.cpp"
    "sdm/libs/core/strategy.cpp"
    "sdm/libs/core/strategy.h"
)

targets=(
    "${required[@]}"
    "sdm/libs/core/mondrian_pu.h"
    "sdm/libs/core/mondrian_pu_geometry.h"
)

if [[ ! -f "$panel_dtsi" ]] ||
   ! grep -q 'qcom,partial-update-enabled = "single_roi"' "$panel_dtsi"; then
    echo "Missing M11A Partial Update devicetree capability (99f1330)." >&2
    exit 1
fi

if [[ ! -f "$dsi_display" ]] ||
   ! grep -q 'm11a_partial_update_profile' "$dsi_display" ||
   [[ ! -f "$sde_crtc" ]] ||
   ! grep -q '_sde_crtc_validate_m11a_scaled_roi' "$sde_crtc"; then
    echo "Missing M11A scaled Partial Update kernel support (5c2241f + 3dbfe7a)." >&2
    exit 1
fi

if [[ ! -d "$display_dir/.git" && ! -f "$display_dir/.git" ]]; then
    echo "Missing Git checkout: hardware/qcom-caf/sm8450/display" >&2
    exit 1
fi

for target in "${required[@]}"; do
    if [[ ! -f "$display_dir/$target" ]]; then
        echo "Unsupported display HAL: missing $target" >&2
        exit 1
    fi
done

project_root="$(git -C "$display_dir" rev-parse --show-toplevel)"
if [[ "$(cd -- "$display_dir" && pwd -P)" != "$(cd -- "$project_root" && pwd -P)" ]]; then
    echo "hardware/qcom-caf/sm8450/display must be its own Git checkout." >&2
    exit 1
fi

if git -C "$display_dir" apply --reverse --check "$patch_file" 2>/dev/null; then
    echo "Mondrian M11A Partial Update HWC/SDM patch is already applied."
    exit 0
fi

status="$(git -C "$display_dir" status --porcelain -- "${targets[@]}")"
if [[ -n "$status" ]]; then
    echo "Display HAL has local changes in Partial Update target files:" >&2
    printf '%s\n' "$status" >&2
    exit 1
fi

if ! git -C "$display_dir" apply --check "$patch_file"; then
    echo "The current sm8450 display HAL is incompatible with the Mondrian Partial Update patch." >&2
    exit 1
fi

git -C "$display_dir" apply "$patch_file"
echo "Applied Mondrian M11A Partial Update HWC/SDM patch."
