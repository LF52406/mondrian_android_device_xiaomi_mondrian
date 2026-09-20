# M11A Partial Update

This is a coordinated kernel, HWC and Settings change for mondrian's primary
M11A command-mode panel. The previously observed WQHD+ full-width partial ROIs
remain the starting point. FHD+ scaling and single-slice rectangles need device
acceptance on the resulting build.

## Required source changes

Apply the complete set before building:

| Project in the ROM checkout | Revision |
| --- | --- |
| `kernel/xiaomi/sm8450-devicetrees` | existing `99f1330d8dce0c80de5a19f425d0a118b2eb7fe3` |
| `kernel/xiaomi/sm8450-modules` | existing `5c2241fef7fb1bd8630a245633f661f882c0bda3`, then `3dbfe7a002830a17baafa0bd12334f7a91e680c6` |
| `hardware/qcom-caf/sm8450/display` | apply `display-settings/patches/0003-M11A-partial-update-HWC-SDM.patch` with `apply-partial-update-display-patch.sh` |
| `device/xiaomi/mondrian` | this feature branch, including Settings, SELinux, diagnostics and the HWC/SDM patch |

The existing common-device `c88f441` refresh-rate change is independent of ROI.
Do not stack the older `mondrian-m11a-partial-update-v2` or
`mondrian-partial-update-scaling` experiments on top of this set.

The HWC/SDM implementation originally developed in commits
`08bf9d4e5e0f16babe411fda7e2f341cfd24761c` and
`23d0ed6bafdb11f8d19cb6e0aaea977fd6bd2dac` is carried here as a source patch.
Those commits are provenance only, not a build dependency. No additional display
repository or fork is required. `hardware/qcom-caf/sm8450/display` below is only
the existing checkout path inside the ROM source tree; it is not a new LF52406
repository.

For a clean checkout, the preferred entry point is the unified stack helper:

```bash
bash device/xiaomi/mondrian/display-settings/apply-partial-update-stack.sh
```

It applies or detects the required devicetree, modules and refresh-category
commits, applies the existing SettingsLib density dependency, installs the HWC/SDM
patch into the display HAL checkout, and runs the production ROI host regression
suite. The lower-level `apply-partial-update-display-patch.sh` remains available
when only the display HAL patch needs to be applied.

The helpers are repeatable. It detects an already applied patch, refuses to touch
dirty target files, checks the patch before applying it, and fails explicitly if
the current HAL API is incompatible. Applying only Settings or only the kernel
does not install the complete feature. Resolve the existing SettingsLib dependency
as documented in the parent README.

## Behavior

Settings → Display → Screen resolution contains one Partial Update switch.
Only the unlocked foreground owner can change it. A pending resolution preview
temporarily disables the switch. The setting persists over reboot.

| Property `persist.sys.mondrian.partial_update` | Policy |
| --- | --- |
| `0` | Full-frame updates |
| `2` or absent | DSC-aligned rectangular Partial Update |

Legacy value `1` is accepted and promoted to rectangular mode `2`, so an OTA
from an earlier test build cannot silently fall back to full-width bands.

Kernel capabilities stay immutable after probe. The kernel advertises the M11A
DSC-slice capability at probe, while HWC either disables Partial Update or uses
the full rectangular DSC-aligned policy. No composer or SurfaceFlinger restart is
needed. A policy transition takes effect on the next composition and first forces
a full frame to clear the previous ROI.

The physical panel remains 1440 × 3200 in both resolution choices. Horizontal
boundaries and widths use 720-pixel slices; vertical boundaries and heights use
32-pixel slices. For a 1080 × 2400 mixer these become 540 × 24. Consequently a
rectangular update may cover either half of the panel, or its full width. It
cannot select an arbitrary 100-pixel-wide physical rectangle.

Constraints are mapped before the partial-update planner runs. Connector ROI is
then projected exactly from mixer coordinates. No DSI-stage rounding or widening
is allowed: the scaler/DSC stream must already cover that exact rectangle. The
kernel checks source geometry, active scaler indices, input dimensions and
physical output dimensions before accepting a scaled partial update.

The proprietary `libsdmextension` still owns QSEED phases and overfetch. A valid
coordinate mapping alone is insufficient. A stale or incompatible resource plan
is rejected, retried with a full frame, and partial planning stays disabled until
the policy or display configuration changes. Detail enhancement, unsupported
topologies, capture and existing display safeguards may also require full frames.
Other panel names do not opt into this feature.

## Checks completed without an Android build

```bash
python3 device/xiaomi/mondrian/display-settings/tests/partial-update/run.py \
  --display hardware/qcom-caf/sm8450/display \
  --modules kernel/xiaomi/sm8450-modules
```

The host suite compiles the production geometry and validation helpers against
small type fixtures with undefined-behavior checks. It covers 16,950,788 axis
projections, both policies, bounds, stale scaler output, incorrect alignment,
right-only updates, duplicate indices and missing scaler flags. The production
retry block is also tested for forced validation, fatal prepare errors and its
single-attempt limit. Resource XML and
patch whitespace have also been checked.

These checks do not compile Android, SELinux or the full kernel, validate the
proprietary resource manager, or exercise QSEED/DSC and the panel. A successful
ROM build and the following device checks are required before claiming FHD+
partial updates or rectangular updates work on the device.

## Device acceptance

Start with SELinux enforcing. In each of FHD+ and WQHD+, check all three policies
at 60, 90 and 120 Hz. Include small UI changes, typing, left/right small controls,
rotation, full-screen animation, screenshot/recording, AOD, unlock and UDFPS.
Switch policies during use and verify the setting after reboot. Look for stale
regions, seams, flicker, freezes, DCS errors and composer restarts.

Copy `capture.sh` to the phone, then run it from a root shell:

```sh
sh /sdcard/Download/capture.sh 15 fhd-rectangles
```

Choose the resolution and enable or disable Partial Update before recording. The script uses its own
tracefs instance and does not clear or change global tracing. If instances are
unavailable, it stops instead. Results are written to a new directory under
`/sdcard/Download/MondrianPU-*`. Inspect `trace-stats.txt` for overruns and repeat
a shorter capture if events were lost.

After copying a capture to the computer:

```bash
python3 device/xiaomi/mondrian/display-settings/partial-update/analyze-trace.py \
  /path/to/capture/trace.txt --mode 2
```

`surfaceflinger-after.txt` includes `MondrianPolicy`, `ScalerBlocked` and
`PlanRejected`. Check the actual mixer size as well as `wm size`: a logical FHD+
size by itself does not prove that the destination scaler is active.

| Result | Interpretation |
| --- | --- |
| Policy 0, all successful ROIs 1440 × 3200 | Disable path behaves as intended |
| Policy 1, width 1440 and some heights below 3200 | Full-width partial updates |
| Policy 2, some widths 720 with x=0 or x=720 and y/height aligned to 32 | Rectangular DSC-slice updates reached DSI |
| `PlanRejected:1` | This resource plan was rejected; the compositor is using full frames |
| `ScalerBlocked:1` | A scaler/detail-enhancement constraint prevents PU |
| All ROIs full frame, no errors | May be valid damage or fallback; does not prove partial updates |
| No ROI events | Not enough evidence; unchanged ROI can be cached |

The original DCS trace is an entry event, before transmission. This patch adds a
completion event with `x|y|width|height|return_code|1`. Only a zero return code on
that event confirms the driver's send completed. This still does not prove the
panel displayed every pixel correctly. Visually inspect both halves and seams.
ROI-command counts are not frame counts or measured battery savings.

For recovery, disable the switch. With an already available root shell the same
policy can be requested using `setprop persist.sys.mondrian.partial_update 0`.
This requests a full frame on the next composition and persists across reboot.
