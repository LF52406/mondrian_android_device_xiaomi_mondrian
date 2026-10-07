#!/usr/bin/env python3
# Copyright (C) 2026 The Android Open Source Project
# SPDX-License-Identifier: Apache-2.0
"""Prepare the shared framework hooks before the Android product is built."""

import argparse
import fcntl
from pathlib import Path
import subprocess
import sys


def git(root, *args):
    return subprocess.run(["git", "-C", str(root), *args], text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def apply_checked_patch(framework, patch, check_only, description):
    if git(framework, "apply", "--reverse", "--check", str(patch)).returncode == 0:
        return

    result = git(framework, "apply", "--check", str(patch))
    if result.returncode:
        raise RuntimeError(
            f"{description} conflicts with this checkout; no changes applied.\n"
            + result.stderr.strip())
    if check_only:
        raise RuntimeError(
            f"{description} is compatible but not applied. Run this script without --check")

    result = git(framework, "apply", str(patch))
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    if git(framework, "apply", "--reverse", "--check", str(patch)).returncode:
        raise RuntimeError(f"{description} verification failed; inspect the checkout")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom-root", type=Path, required=True)
    parser.add_argument("--check", action="store_true", help="Check integration without applying it")
    args = parser.parse_args()
    framework = args.rom_root.resolve() / "frameworks/base"
    patches = Path(__file__).resolve().parent / "patches"
    base_patch = patches / "0001-mondrian-haptic-engine.patch"
    system_ui_patch = patches / "0002-mondrian-haptic-system-ui.patch"
    upgrades = [
        (patches / "previous/0002-mondrian-haptic-engine.patch",
         patches / "upgrades/0002-to-0003.patch"),
        (patches / "previous/0001-mondrian-haptic-engine.patch",
         patches / "upgrades/0001-to-0003.patch"),
    ]
    root = git(framework, "rev-parse", "--show-toplevel")
    if root.returncode or Path(root.stdout.strip()).resolve() != framework:
        raise RuntimeError("frameworks/base must be a Git checkout in the selected ROM root")
    if not base_patch.is_file() or not system_ui_patch.is_file():
        raise RuntimeError("The device tree is missing a Haptic Engine framework patch")

    lock_path = Path(git(framework, "rev-parse", "--git-path", "mondrian-haptics.lock").stdout.strip())
    if not lock_path.is_absolute():
        lock_path = framework / lock_path

    with lock_path.open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)

        # The extension changes files that are also touched by the base patch, so once
        # both patches are installed the base patch is no longer independently
        # reverse-checkable. Detect the final state first to keep repeated runs and
        # --check idempotent.
        if git(framework, "apply", "--reverse", "--check",
               str(system_ui_patch)).returncode == 0:
            print("ok")
            return

        if git(framework, "apply", "--reverse", "--check", str(base_patch)).returncode != 0:
            result = git(framework, "apply", "--check", str(base_patch))
            selected_patch = base_patch
            if result.returncode:
                # Upgrade only a complete, recognizable previous installation. Preserve local edits.
                for previous, upgrade in upgrades:
                    if (previous.is_file() and upgrade.is_file()
                            and git(framework, "apply", "--reverse", "--check", str(previous)).returncode == 0
                            and git(framework, "apply", "--check", str(upgrade)).returncode == 0):
                        selected_patch = upgrade
                        break
                else:
                    raise RuntimeError(
                        "Framework hooks conflict with this checkout; no changes applied. "
                        "Review the framework revision or a partially applied patch.\n"
                        + result.stderr.strip())
            if args.check:
                raise RuntimeError(
                    "Base Haptic Engine patch is compatible but not applied. "
                    "Run this script without --check")
            result = git(framework, "apply", str(selected_patch))
            if result.returncode:
                raise RuntimeError(result.stderr.strip())
            if git(framework, "apply", "--reverse", "--check", str(base_patch)).returncode:
                raise RuntimeError("Base Haptic Engine patch verification failed; inspect the checkout")

        apply_checked_patch(
            framework,
            system_ui_patch,
            args.check,
            "Haptic Engine SystemUI/Pixel Launcher extension")

        print("ok")


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
