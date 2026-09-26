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
echo "Mondrian display framework patches are ready."
echo "Build Settings/MondrianDisplaySettings and BootAnimation as required."
