#!/usr/bin/env bash
# Temporary mondrian-only Miracast GPU virtual-display workaround for MistOS 17.0.
set -euo pipefail

root="${1:-$PWD}"
source_file="$root/frameworks/native/services/surfaceflinger/SurfaceFlinger.cpp"

if [[ ! -f "$source_file" ]]; then
    echo "ERROR: SurfaceFlinger.cpp not found: $source_file" >&2
    exit 1
fi

python3 - "$source_file" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
content = path.read_text()
marker = 'debug.sf.mondrian_force_gpu_wfd'
anchor = '    bool canAllocate = mAllowHwcForVDS || (isWfd && mAllowHwcForWFD) || (isWfd &&'

if marker in content:
    if content.count(marker) != 1:
        raise SystemExit('ERROR: unexpected duplicate Miracast workaround; refusing to modify source')
    print('Miracast GPU virtual-display workaround already applied')
    raise SystemExit(0)

# Fail closed rather than modifying a different or obsolete SurfaceFlinger implementation.
if content.count(anchor) != 1 or 'bool SurfaceFlinger::canAllocateHwcDisplayIdForVDS(uint64_t usage)' not in content:
    raise SystemExit('ERROR: unsupported frameworks/native revision; patch was NOT applied')

insert = '''    // Mondrian's Qualcomm WFD HWC path supplies an output buffer without GPU write usage.
    // Force only Wi-Fi Display onto the GPU virtual-display path. Keep the RenderEngine
    // buffer-usage validation intact, and do not affect the physical display or other VDS.
    if (isWfd && base::GetBoolProperty("debug.sf.mondrian_force_gpu_wfd"s, false)) {
        ALOGI("Mondrian WFD: using GPU virtual display (HWC bypass)");
        return false;
    }

'''
content = content.replace(anchor, insert + anchor, 1)
path.write_text(content)
print('Applied mondrian Wi-Fi Display GPU virtual-display workaround to frameworks/native')
PY

if git -C "$root/frameworks/native" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git -C "$root/frameworks/native" diff --check -- services/surfaceflinger/SurfaceFlinger.cpp
fi
