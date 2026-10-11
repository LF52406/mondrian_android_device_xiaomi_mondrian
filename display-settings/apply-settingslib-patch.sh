#!/usr/bin/env bash
# Copyright (C) 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
rom_root="${1:-${ANDROID_BUILD_TOP:-$PWD}}"
framework_dir="$rom_root/frameworks/base"
settingslib_patch="$script_dir/patches/0001-SettingsLib-mondrian-logical-density.patch"
settingslib_target="packages/SettingsLib/src/com/android/settingslib/display/DisplayDensityUtils.java"
bootanimation_script="$script_dir/apply-bootanimation-patch.sh"
bootanimation_patch="$script_dir/patches/0002-BootAnimation-mondrian-logical-geometry.patch"
hwc_dir="$rom_root/hardware/qcom-caf/sm8450/display"
hwc_patch="$script_dir/patches/0003-HWC-mondrian-partial-update-handshake.patch"
hwc_upgrade_patch="$script_dir/patches/0005-HWC-mondrian-worker-recovery.patch"
sdm_roi_guard_patch="$script_dir/patches/0006-SDM-mondrian-ROI-off-vote.patch"
kernel_dir="$rom_root/kernel/xiaomi/sm8450-modules"
kernel_patch="$script_dir/patches/0004-M11A-full-frame-ROI-dirty.patch"

# One public entry point. No apply-display-patches.sh wrapper is required.
# Check existing patches and all independent targets before changing sources.
# For clean HWC trees, the follow-up worker fix is checked immediately after
# its prerequisite adds composer/hwc_mondrian.cpp.
preflight_patch() {
    local project="$1" patch="$2" name="$3"
    if git -C "$project" apply --reverse --check "$patch" 2>/dev/null; then
        echo "==> $name: already applied"
    elif git -C "$project" apply --check "$patch"; then
        echo "==> $name: ready"
    else
        echo "Cannot apply $name to $project; no sources were changed." >&2
        exit 1
    fi
}

apply_patch_once() {
    local project="$1" patch="$2" name="$3"
    if git -C "$project" apply --reverse --check "$patch" 2>/dev/null; then
        echo "==> $name: already applied"
    else
        git -C "$project" apply "$patch"
        echo "==> $name: applied"
    fi
}

if [[ ! -f "$hwc_dir/composer/hwc_session.cpp" ]]; then
    echo "Missing Qualcomm HWC source at $hwc_dir" >&2
    exit 1
fi
if [[ ! -f "$kernel_dir/qcom/opensource/display-drivers/msm/sde/sde_crtc.c" ]]; then
    echo "Missing Qualcomm kernel modules at $kernel_dir" >&2
    exit 1
fi
for repo_dir in "$framework_dir" "$hwc_dir" "$kernel_dir"; do
    repo_root="$(git -C "$repo_dir" rev-parse --show-toplevel)" || exit 1
    if [[ "$(cd "$repo_dir" && pwd -P)" != "$(cd "$repo_root" && pwd -P)" ]]; then
        echo "Expected independent Git checkout: $repo_dir" >&2
        exit 1
    fi
done
preflight_patch "$framework_dir" "$settingslib_patch" "SettingsLib"
preflight_patch "$framework_dir" "$bootanimation_patch" "BootAnimation"
if git -C "$hwc_dir" apply --reverse --check "$hwc_upgrade_patch" 2>/dev/null; then
    echo "==> Qualcomm HWC base + recovery: already applied"
else
    preflight_patch "$hwc_dir" "$hwc_patch" "Qualcomm HWC base"
    if [[ -f "$hwc_dir/composer/hwc_mondrian.cpp" ]]; then
        preflight_patch "$hwc_dir" "$hwc_upgrade_patch" "Qualcomm HWC recovery"
    fi
fi
preflight_patch "$hwc_dir" "$sdm_roi_guard_patch" "Qualcomm SDM ROI OFF guard"
preflight_patch "$kernel_dir" "$kernel_patch" "M11A ROI kernel"

if [[ ! -f "$framework_dir/$settingslib_target" ]]; then
    echo "Run from the ROM root, or pass its path as the first argument." >&2
    exit 1
fi

project_root="$(git -C "$framework_dir" rev-parse --show-toplevel)"
if [[ "$(cd -- "$framework_dir" && pwd -P)" != "$(cd -- "$project_root" && pwd -P)" ]]; then
    echo "frameworks/base must be its own Git checkout." >&2
    exit 1
fi

echo "==> Mondrian SettingsLib logical-density patch"

if git -C "$framework_dir" apply --reverse --check "$settingslib_patch" 2>/dev/null; then
    echo "Mondrian SettingsLib density patch is already applied."
else
    if ! git -C "$framework_dir" diff --quiet HEAD -- "$settingslib_target"; then
        echo "DisplayDensityUtils.java has local changes; review them before applying this patch." >&2
        exit 1
    fi

    git -C "$framework_dir" apply --check "$settingslib_patch"
    git -C "$framework_dir" apply "$settingslib_patch"
    echo "Applied the Mondrian SettingsLib density patch."
fi

if [[ ! -f "$bootanimation_script" ]]; then
    echo "Missing $bootanimation_script" >&2
    exit 1
fi

echo
echo "==> Mondrian BootAnimation logical-geometry patch"
bash "$bootanimation_script" "$rom_root"

echo
echo "==> Mondrian Qualcomm HWC and kernel ROI stabilization"
if git -C "$hwc_dir" apply --reverse --check "$hwc_upgrade_patch" 2>/dev/null; then
    echo "==> Qualcomm HWC base + recovery: already applied"
else
    apply_patch_once "$hwc_dir" "$hwc_patch" "Qualcomm HWC base"
    apply_patch_once "$hwc_dir" "$hwc_upgrade_patch" "Qualcomm HWC recovery"
fi
apply_patch_once "$hwc_dir" "$sdm_roi_guard_patch" "Qualcomm SDM ROI OFF guard"
apply_patch_once "$kernel_dir" "$kernel_patch" "M11A ROI kernel"

echo
echo "All Mondrian display source patches are ready."
echo "MondrianDisplaySettings is installed by device.mk during the ROM build."
