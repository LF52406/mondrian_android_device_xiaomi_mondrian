#!/usr/bin/env bash
# Copyright (C) 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
# One entry point for Mondrian display framework and Qualcomm HWC integration.
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
rom_root="${1:-${ANDROID_BUILD_TOP:-$PWD}}"
hwc_dir="${2:-$rom_root/hardware/qcom-caf/sm8450/display}"
hwc_patch="$script_dir/patches/0003-HWC-mondrian-partial-update-handshake.patch"

if [[ ! -f "$rom_root/build/envsetup.sh" ]]; then
    echo "Missing Android source tree at: $rom_root" >&2
    exit 1
fi
if [[ ! -f "$hwc_dir/composer/hwc_session.cpp" ]]; then
    echo "Missing Qualcomm SM8450 display tree at: $hwc_dir" >&2
    exit 1
fi

project_root="$(git -C "$hwc_dir" rev-parse --show-toplevel)"
if [[ "$(cd -- "$hwc_dir" && pwd -P)" != "$(cd -- "$project_root" && pwd -P)" ]]; then
    echo "Qualcomm display sources must be their own Git checkout." >&2
    exit 1
fi

# Preflight HWC BEFORE making framework changes. An already-applied patch is
# fine; unrelated changes to HWC sources are never silently overwritten.
if git -C "$hwc_dir" apply --reverse --check "$hwc_patch" 2>/dev/null; then
    hwc_action="already-applied"
else
    for target in composer/Android.bp composer/hwc_session.cpp composer/hwc_session.h composer/hwc_mondrian.cpp; do
        if ! git -C "$hwc_dir" diff --quiet HEAD -- "$target"; then
            echo "Review pre-existing Qualcomm display changes in $target first." >&2
            exit 1
        fi
    done
    git -C "$hwc_dir" apply --check "$hwc_patch"
    hwc_action="apply"
fi

# The existing script already applies both SettingsLib and BootAnimation.
echo "==> SettingsLib and BootAnimation"
bash "$script_dir/apply-settingslib-patch.sh" "$rom_root"

echo "==> Qualcomm HWC M11A Partial Update"
if [[ "$hwc_action" == "apply" ]]; then
    git -C "$hwc_dir" apply "$hwc_patch"
    echo "Applied Mondrian HWC integration."
else
    echo "Mondrian HWC integration already applied."
fi

echo "Mondrian display source patches ready."
