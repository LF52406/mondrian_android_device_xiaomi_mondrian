#!/usr/bin/env bash
# Copyright (C) 2026 The LineageOS Project; SPDX-License-Identifier: Apache-2.0
set -euo pipefail
patch_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
rom_root="${1:-${ANDROID_BUILD_TOP:-$PWD}}"
framework_dir="$rom_root/frameworks/base"
patch_file="$patch_dir/patches/0001-SettingsLib-mondrian-logical-density.patch"
target_file="packages/SettingsLib/src/com/android/settingslib/display/DisplayDensityUtils.java"

if [[ ! -f "$framework_dir/$target_file" ]]; then
    echo "Run from the ROM root, or pass its path as the first argument." >&2
    exit 1
fi
project_root="$(git -C "$framework_dir" rev-parse --show-toplevel)"
if [[ "$(cd -- "$framework_dir" && pwd -P)" != "$(cd -- "$project_root" && pwd -P)" ]]; then
    echo "frameworks/base must be its own Git checkout." >&2
    exit 1
fi
if git -C "$framework_dir" apply --reverse --check "$patch_file" 2>/dev/null; then
    echo "Mondrian SettingsLib density patch is already applied."
    exit 0
fi
if ! git -C "$framework_dir" diff --quiet HEAD -- "$target_file"; then
    echo "DisplayDensityUtils.java has local changes; review them before applying this patch." >&2
    exit 1
fi
git -C "$framework_dir" apply --check "$patch_file"
git -C "$framework_dir" apply "$patch_file"
echo "Applied the Mondrian SettingsLib density patch. Build Settings and MondrianDisplaySettings."
