#!/usr/bin/env bash
# Copyright (C) 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
rom_root="${1:-${ANDROID_BUILD_TOP:-$PWD}}"
hwc_dir="${2:-$rom_root/hardware/qcom-caf/sm8450/display}"
hwc_patch="$script_dir/patches/0003-HWC-mondrian-partial-update-handshake.patch"

if [[ ! -f "$hwc_dir/composer/hwc_session.cpp" ]]; then
    echo "Missing Qualcomm SM8450 display tree: $hwc_dir" >&2
    exit 1
fi

project_root="$(git -C "$hwc_dir" rev-parse --show-toplevel)"
if [[ "$(cd -- "$hwc_dir" && pwd -P)" != "$(cd -- "$project_root" && pwd -P)" ]]; then
    echo "hardware/qcom-caf/sm8450/display must be its own Git checkout." >&2
    exit 1
fi

if git -C "$hwc_dir" apply --reverse --check "$hwc_patch" 2>/dev/null; then
    echo "Mondrian HWC Partial Update handshake is already applied."
    exit 0
fi

if ! git -C "$hwc_dir" diff --quiet HEAD -- \
        composer/Android.bp composer/hwc_session.cpp composer/hwc_session.h; then
    echo "Qualcomm display files touched by the Mondrian patch have local changes." >&2
    echo "Review or commit them before applying this patch." >&2
    exit 1
fi

git -C "$hwc_dir" apply --check "$hwc_patch"
git -C "$hwc_dir" apply "$hwc_patch"

echo "Applied Mondrian HWC Partial Update handshake to: $hwc_dir"
