#!/usr/bin/env bash
# Copyright (C) 2026 The LineageOS Project; SPDX-License-Identifier: Apache-2.0
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
rom_root="${1:-${ANDROID_BUILD_TOP:-$PWD}}"
hwc_dir="${2:-$rom_root/hardware/qcom-caf/sm8450/display}"
framework_dir="$rom_root/frameworks/base"
patches="$script_dir/patches"
framework_patch="$patches/0001-SettingsLib-mondrian-logical-density.patch"
hwc_patch="$patches/0003-HWC-mondrian-partial-update-handshake.patch"

# Preflight both repositories before changing either. Never guess through a conflict.
for project in "$framework_dir" "$hwc_dir"; do
    [[ "$(git -C "$project" rev-parse --show-toplevel)" == "$(cd "$project" && pwd -P)" ]] || {
        echo "Expected a separate Git checkout: $project" >&2; exit 1;
    }
done
if ! git -C "$framework_dir" apply --reverse --check "$framework_patch" 2>/dev/null; then
    if ! git -C "$framework_dir" apply --check "$framework_patch" 2>/dev/null; then
        framework_patch="$patches/0001a-SettingsLib-upgrade-device-detection.patch"
        git -C "$framework_dir" apply --check "$framework_patch"
    fi
fi
if ! git -C "$hwc_dir" apply --reverse --check "$hwc_patch" 2>/dev/null; then
    git -C "$hwc_dir" apply --check "$hwc_patch"
fi
for pair in framework hwc; do
    if [[ "$pair" == framework ]]; then
        project="$framework_dir"; patch="$framework_patch"
    else
        project="$hwc_dir"; patch="$hwc_patch"
    fi
    if git -C "$project" apply --reverse --check "$patch" 2>/dev/null; then
        echo "$pair: already applied"
    else
        git -C "$project" apply "$patch"
        echo "$pair: applied"
    fi
done
