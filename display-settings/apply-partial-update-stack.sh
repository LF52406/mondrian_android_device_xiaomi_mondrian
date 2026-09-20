#!/usr/bin/env bash
# Copyright (C) 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

rom_root="${1:-${ANDROID_BUILD_TOP:-$PWD}}"

apply_commit() {
    local repo="$1"
    local url="$2"
    local branch="$3"
    local commit="$4"
    local mark

    if [[ ! -d "$rom_root/$repo" ]]; then
        echo "Missing source checkout: $repo" >&2
        exit 1
    fi

    git -C "$rom_root/$repo" fetch "$url" "$branch"

    if git -C "$rom_root/$repo" merge-base --is-ancestor "$commit" HEAD 2>/dev/null; then
        return 0
    fi

    mark="$(git -C "$rom_root/$repo" cherry HEAD "$commit" "$commit^" 2>/dev/null | awk 'NR==1 {print $1}')"
    if [[ "$mark" == "-" ]]; then
        return 0
    fi

    git -C "$rom_root/$repo" cherry-pick "$commit"
}

apply_commit     kernel/xiaomi/sm8450-devicetrees     https://github.com/LF52406/android_kernel_xiaomi_sm8450-devicetrees.git     codex/mondrian-m11a-partial-update     99f1330d8dce0c80de5a19f425d0a118b2eb7fe3

apply_commit     kernel/xiaomi/sm8450-modules     https://github.com/LF52406/android_kernel_xiaomi_sm8450-modules.git     codex/mondrian-partial-update-controls     5c2241fef7fb1bd8630a245633f661f882c0bda3

apply_commit     kernel/xiaomi/sm8450-modules     https://github.com/LF52406/android_kernel_xiaomi_sm8450-modules.git     codex/mondrian-partial-update-controls     3dbfe7a002830a17baafa0bd12334f7a91e680c6

apply_commit     device/xiaomi/sm8450-common     https://github.com/LF52406/mondrian_android_device_xiaomi_sm8450-common.git     codex/mondrian-content-aware-refresh     c88f441afbd6d1c50ebe6848147aece808de7788

# MondrianDisplaySettings calls the public SettingsLib density getter supplied
# by the existing screen-resolution companion patch. Keep the unified setup
# self-contained for a clean ROM checkout.
bash "$rom_root/device/xiaomi/mondrian/display-settings/apply-settingslib-patch.sh" "$rom_root"

bash "$rom_root/device/xiaomi/mondrian/display-settings/apply-partial-update-display-patch.sh" "$rom_root"

# Compile and execute the production ROI geometry/validation helpers against
# host fixtures before spending time on a full Android build.
python3 "$rom_root/device/xiaomi/mondrian/display-settings/tests/partial-update/run.py" \
    --display "$rom_root/hardware/qcom-caf/sm8450/display" \
    --modules "$rom_root/kernel/xiaomi/sm8450-modules"
