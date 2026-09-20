#!/system/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Run with root: sh capture.sh 15 fhd-rectangles
set -eu

[ "$(id -u)" = 0 ] || { echo 'Run this script with root.' >&2; exit 1; }
duration=${1:-15}
label=${2:-capture}
case "$duration" in ''|*[!0-9]*) echo 'Duration must be 1..60 seconds.' >&2; exit 1;; esac
[ "$duration" -ge 1 ] && [ "$duration" -le 60 ] || exit 1
case "$label" in ''|*[!A-Za-z0-9_-]*) echo 'Use letters, numbers, _ or - in the label.' >&2; exit 1;; esac

trace_root=/sys/kernel/tracing
[ -d "$trace_root/events" ] || trace_root=/sys/kernel/debug/tracing
[ -f "$trace_root/events/sde/sde_evtlog/enable" ] || {
    echo 'SDE tracepoint unavailable in this kernel.' >&2; exit 1;
}
instance="$trace_root/instances/mondrian_pu_$$"
mkdir "$instance" || {
    echo 'Cannot create an isolated tracing instance. Global tracing was not changed.' >&2; exit 1;
}
cleanup() {
    echo 0 > "$instance/tracing_on" 2>/dev/null || true
    echo 0 > "$instance/events/sde/sde_evtlog/enable" 2>/dev/null || true
    rmdir "$instance" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

out="/sdcard/Download/MondrianPU-$label-$(date +%Y%m%d-%H%M%S)-$$"
mkdir "$out"
{
    echo "label=$label"
    echo "mode=$(getprop persist.sys.mondrian.partial_update)"
    echo "build=$(getprop ro.build.fingerprint)"
    echo "device=$(getprop ro.product.device)"
    echo "selinux=$(getenforce)"
    wm size
    wm density
    for profile in /sys/module/*/parameters/m11a_partial_update_profile; do
        [ -f "$profile" ] || continue
        echo "$profile=$(cat "$profile")"
    done
} > "$out/context.txt" 2>&1
dumpsys SurfaceFlinger > "$out/surfaceflinger-before.txt" 2>&1 || true

echo 0 > "$instance/tracing_on"
echo 2048 > "$instance/buffer_size_kb"
echo 1 > "$instance/events/sde/sde_evtlog/enable"
echo 1 > "$instance/tracing_on"
echo "Recording for $duration seconds: type, open small controls, and move a small window."
sleep "$duration"
echo 0 > "$instance/tracing_on"
cat "$instance/trace" > "$out/trace.txt"
for stats in "$instance"/per_cpu/cpu*/stats; do
    [ -f "$stats" ] || continue
    echo "$stats"
    cat "$stats"
done > "$out/trace-stats.txt"
dumpsys SurfaceFlinger > "$out/surfaceflinger-after.txt" 2>&1 || true
logcat -b all -d -v threadtime -t 1500 -s SDM:I auditd:W SELinux:W \
    > "$out/display-logcat.txt" 2>&1 || true
dmesg | grep -Ei 'm11a|sde|dsi|avc:.*(graphics_composer|mondrian_partial_update)' \
    | tail -n 300 > "$out/display-kernel.txt" || true
echo "Saved: $out"

