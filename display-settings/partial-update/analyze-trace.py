#!/usr/bin/env python3
"""Summarize ROI command attempts, not frame counts or power savings."""
import argparse
from collections import Counter
from pathlib import Path
import re


def analyze(text):
    entries, completed = [], []
    for line in text.splitlines():
        match = re.search(r'dsi_panel_send_roi_dcs:\d+\|([^\s]+)', line)
        if not match:
            continue
        try:
            values = [int(value, 16) for value in match.group(1).split('|') if value]
        except ValueError:
            continue
        if len(values) < 4:
            continue
        if len(values) >= 6 and values[5] == 1:
            status = values[4]
            if status & 0x80000000:
                status -= 1 << 32
            completed.append((tuple(values[:4]), status))
        else:
            entries.append(tuple(values[:4]))
    return entries, completed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('trace', type=Path)
    parser.add_argument('--mode', type=int, choices=(0, 2))
    args = parser.parse_args()
    entries, completed = analyze(args.trace.read_text(errors='replace'))
    good = [roi for roi, status in completed if status == 0]
    errors = [(roi, status) for roi, status in completed if status != 0]
    rois = good if completed else entries
    counts = Counter(rois)
    invalid = [roi for roi in rois if
               roi[0] < 0 or roi[1] < 0 or roi[2] <= 0 or roi[3] <= 0 or
               roi[0] + roi[2] > 1440 or roi[1] + roi[3] > 3200 or
               roi[0] % 720 or roi[2] % 720 or roi[1] % 32 or roi[3] % 32]
    policy_errors = [roi for roi in rois
                     if args.mode == 0 and roi != (0, 0, 1440, 3200)]
    print(f'Entry events: {len(entries)}; completed: {len(completed)}; errors: {len(errors)}')
    if not completed:
        print('No completion markers: entries alone cannot prove successful DCS transmission.')
    left = sum(n for (x, _, w, _), n in counts.items() if x == 0 and w == 720)
    right = sum(n for (x, _, w, _), n in counts.items() if x == 720 and w == 720)
    rectangles = sum(n for (_, _, w, h), n in counts.items()
                     if w < 1440 and h < 3200)
    print(f'Full frame: {counts[(0, 0, 1440, 3200)]}; '
          f'narrower than panel: {sum(n for (_, _, w, _), n in counts.items() if w < 1440)}; '
          f'shorter than panel: {sum(n for (_, _, _, h), n in counts.items() if h < 3200)}; '
          f'true 2D rectangles: {rectangles}')
    print(f'DSC half-width updates: left={left}; right={right}')
    for (x, y, w, h), count in counts.most_common(12):
        print(f'{count:5d}  x={x:4d} y={y:4d} width={w:4d} height={h:4d}')
    for roi, status in errors[:12]:
        print(f'DCS error {status}: {roi}')
    print(f'Invalid geometry: {len(invalid)}; policy mismatches: {len(policy_errors)}')
    print('ROI commands may be cached between frames. These are not frame or energy counters.')
    return 1 if invalid or errors or policy_errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
