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

# Verify the actual two-stage HWC patch chain, including reapplication against
# a previous test build with the base HWC patch already installed.
patches="$test_dir/../patches"
hwc_repo="$test_classes/hwc-replay"
mkdir -p "$hwc_repo"
git -C "$hwc_repo" init -q

# The base patch creates the mondrian C++ file. Exclude unrelated Qualcomm
# sources here; the real ROM script preflights those against its HWC checkout.
git -C "$hwc_repo" apply --include='composer/hwc_mondrian.cpp' \
    "$patches/0003-HWC-mondrian-partial-update-handshake.patch"
git -C "$hwc_repo" apply --check \
    "$patches/0005-HWC-mondrian-worker-recovery.patch"
git -C "$hwc_repo" apply \
    "$patches/0005-HWC-mondrian-worker-recovery.patch"
git -C "$hwc_repo" apply --reverse --check \
    "$patches/0005-HWC-mondrian-worker-recovery.patch"
# Simulate an older composer checkout upgraded in place without reapplying
# any other display feature or overwriting local changes.
git -C "$hwc_repo" apply --reverse \
    "$patches/0005-HWC-mondrian-worker-recovery.patch"
git -C "$hwc_repo" apply --check \
    "$patches/0005-HWC-mondrian-worker-recovery.patch"
git -C "$hwc_repo" apply \
    "$patches/0005-HWC-mondrian-worker-recovery.patch"
git -C "$hwc_repo" apply --reverse --check \
    "$patches/0005-HWC-mondrian-worker-recovery.patch"

grep -q 'Mondrian: starting HWC ROI transaction worker' \
    "$hwc_repo/composer/hwc_mondrian.cpp"
if grep -q 'property_get_bool(kControl' "$hwc_repo/composer/hwc_mondrian.cpp"; then
    echo "FAIL: HWC worker still depends on an unavailable property" >&2
    exit 1
fi
echo "PASS: HWC patch chain and idempotent upgrade"

# Catch malformed Soong hunk counts (a previous test build lost a closing }).
bp_repo="$test_classes/hwc-soong"
mkdir -p "$bp_repo/composer"
git -C "$bp_repo" init -q
for ((n=0; n<94; n++)); do
    echo "// upstream placeholder"
done > "$bp_repo/composer/Android.bp"
cat >> "$bp_repo/composer/Android.bp" <<'EOF'
    sub_dir: "vintf/manifest",
    vendor: true,
}
EOF
git -C "$bp_repo" apply --check --include='composer/Android.bp' \
    "$patches/0003-HWC-mondrian-partial-update-handshake.patch"
git -C "$bp_repo" apply --include='composer/Android.bp' \
    "$patches/0003-HWC-mondrian-partial-update-handshake.patch"
tail -n 1 "$bp_repo/composer/Android.bp" | grep -qx '}'
echo "PASS: Qualcomm composer Soong brace and patch size"

# Verify that the actual HWC recovery source has the fault-path invariant:
# never program profile 0 after a failed/unconfirmed full-frame present.
python3 - "$hwc_repo/composer/hwc_mondrian.cpp" <<'PY'
from pathlib import Path
import sys

code = Path(sys.argv[1]).read_text()
start = code.index("bool HWCSession::ProcessMondrianPartialUpdate(")
end = code.index("void HWCSession::StartMondrianPartialUpdate()", start)
transition = code[start:end]
assert transition.index("SetRoiAllowed(false)") < transition.index(
    "MondrianCommitPartialUpdate(false)")
assert transition.index("ProgramKernelProfile(request, profile)") < transition.index(
    "SetRoiAllowed(true)")
fallback = transition[transition.index("A failed HWC enable"):]
assert "ProgramKernelProfile(request, 0)" not in fallback
assert "if (!MondrianCommitPartialUpdate(false)) {" in fallback
assert "kernel stays at profile 1" in fallback
assert "last_ok = false" in code
assert "if (!display_on)" in code
assert "previous.clear();" in code
print("PASS: kernel OFF gated by retired full-frame, revalidation on wake")
PY

# On actual Android source trees, verify the separate Qualcomm SDM guard
# against the user's CAF checkout without modifying that checkout.
rom_root="$(cd "$test_dir/../../../../.." && pwd -P)"
hwc_real="$rom_root/hardware/qcom-caf/sm8450/display"
if [[ -f "$hwc_real/sdm/libs/core/display_builtin.cpp" ]]; then
    if git -C "$hwc_real" apply --reverse --check \
            "$patches/0006-SDM-mondrian-ROI-off-vote.patch" 2>/dev/null; then
        echo "PASS: Qualcomm SDM guard already installed"
    else
        git -C "$hwc_real" apply --check \
            "$patches/0006-SDM-mondrian-ROI-off-vote.patch"
        echo "PASS: Qualcomm SDM guard matches the clean CAF checkout"
    fi
fi
