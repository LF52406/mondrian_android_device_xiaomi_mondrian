#!/usr/bin/env bash
# Copyright (C) 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

patch_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
rom_root="${1:-${ANDROID_BUILD_TOP:-$PWD}}"
framework_dir="$rom_root/frameworks/base"
patch_file="$patch_dir/patches/0002-BootAnimation-mondrian-logical-geometry.patch"
targets=(
    "cmds/bootanimation/BootAnimation.cpp"
    "cmds/bootanimation/BootAnimation.h"
)

for target in "${targets[@]}"; do
    if [[ ! -f "$framework_dir/$target" ]]; then
        echo "Missing frameworks/base/$target. Run from the ROM root, or pass it as the first argument." >&2
        exit 1
    fi
done

project_root="$(git -C "$framework_dir" rev-parse --show-toplevel)"
if [[ "$(cd -- "$framework_dir" && pwd -P)" != "$(cd -- "$project_root" && pwd -P)" ]]; then
    echo "frameworks/base must be its own Git checkout." >&2
    exit 1
fi

if git -C "$framework_dir" apply --reverse --check "$patch_file" 2>/dev/null; then
    echo "Mondrian BootAnimation logical-geometry patch is already applied."
    exit 0
fi

for target in "${targets[@]}"; do
    if ! git -C "$framework_dir" diff --quiet HEAD -- "$target"; then
        echo "$target has local changes; review them before applying this patch." >&2
        exit 1
    fi
done

git -C "$framework_dir" apply --check "$patch_file"
git -C "$framework_dir" apply "$patch_file"

echo "Applied Mondrian BootAnimation logical-geometry patch."
