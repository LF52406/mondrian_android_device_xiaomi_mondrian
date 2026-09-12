#!/usr/bin/env bash
# Copyright (C) 2026 The LineageOS Project; SPDX-License-Identifier: Apache-2.0
set -euo pipefail
test_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
test_classes="$(mktemp -d)"
trap 'rm -rf -- "$test_classes"' EXIT
test_source="$test_dir/../src/org/lineageos/mondrian/display"
java com.sun.tools.javac.Main -d "$test_classes" \
    "$test_source/ResolutionEngine.java" \
    "$test_source/StateCodec.java" \
    "$test_dir/ResolutionEngineTest.java"
java -cp "$test_classes" org.lineageos.mondrian.display.ResolutionEngineTest
