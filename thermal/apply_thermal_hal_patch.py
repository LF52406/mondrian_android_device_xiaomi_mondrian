#!/usr/bin/env python3
"""Stage-1 mondrian/Android 17 skin-sensor HAL integration (explicit opt-in).

Modify the checked-out QTI AIDL thermal HAL in place only when --apply is given.
The existing Xiaomi thermal daemon, kernel cooling and Power HAL are untouched.
"""

import argparse
import os
from pathlib import Path
import sys
import tempfile

INCLUDE = '#include <android-base/properties.h>  // MONDRIAN_SKIN_A17_INCLUDE\n'
INCLUDE_ANCHOR = '#include <android-base/logging.h>\n'
CONFIG_ANCHOR = 'thermalConfig = add_target_config(soc_id, hwPlatform, limitp, it->second);'
START_MARKER = '// MONDRIAN_SKIN_A17_BEGIN'
END_MARKER = '// MONDRIAN_SKIN_A17_END'
INSERTION = '''
		// MONDRIAN_SKIN_A17_BEGIN
		// Poco F5 Pro (Cape, SoC 530): xo-therm is absent on device.
		// This maps the existing vendor SKIN thresholds to a real thermal zone.
		// Trial only: verify quiet_therm versus measured enclosure temperature.
		if (soc_id == 530 &&
		    android::base::GetProperty("ro.product.device", "") == "mondrian") {
			for (auto& sensor : thermalConfig) {
				if (sensor.type == TemperatureType::SKIN &&
				    sensor.sensor_list.size() == 1 &&
				    sensor.sensor_list[0] == "xo-therm") {
					sensor.sensor_list[0] = "quiet_therm";
					LOG(INFO) << "mondrian: reporting SKIN via quiet_therm";
				}
			}
		}
		// MONDRIAN_SKIN_A17_END
'''


class PatchError(ValueError):
    pass


def apply_patch(source: str) -> str:
    if START_MARKER in source:
        if source.count(START_MARKER) != 1 or source.count(END_MARKER) != 1:
            raise PatchError('Malformed existing mondrian patch')
        return source
    if END_MARKER in source:
        raise PatchError('Unexpected patch end marker')
    if source.count(CONFIG_ANCHOR) != 1 or INCLUDE_ANCHOR not in source:
        raise PatchError('Unsupported QTI thermalConfig.cpp: expected anchors missing')
    if 'waipio_specific' not in source or '"xo-therm"' not in source:
        raise PatchError('QTI Cape/Waipio SKIN configuration was not found')
    updated = source
    if '#include <android-base/properties.h>' not in updated:
        updated = updated.replace(INCLUDE_ANCHOR, INCLUDE_ANCHOR + INCLUDE, 1)
    return updated.replace(CONFIG_ANCHOR, CONFIG_ANCHOR + INSERTION, 1)


def revert_patch(source: str) -> str:
    if START_MARKER not in source and END_MARKER not in source:
        return source
    if source.count(START_MARKER) != 1 or source.count(END_MARKER) != 1:
        raise PatchError('Malformed patch; refusing to alter file')
    if source.count(CONFIG_ANCHOR + INSERTION) != 1:
        raise PatchError('Patch body was modified; refusing automatic revert')
    clean = source.replace(CONFIG_ANCHOR + INSERTION, CONFIG_ANCHOR, 1)
    return clean.replace(INCLUDE, '', 1)


def atomic_write(path: Path, content: str) -> None:
    fd, name = tempfile.mkstemp(prefix='.thermalConfig.', dir=path.parent)
    try:
        os.fchmod(fd, path.stat().st_mode & 0o777)
        with os.fdopen(fd, 'w', encoding='utf-8', newline='') as stream:
            stream.write(content)
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tree', type=Path, default=Path(os.environ.get('ANDROID_BUILD_TOP', '.')),
                        help='Root of checked-out Android 17 source tree')
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--check', action='store_true', help='Validate only; make no changes')
    group.add_argument('--apply', action='store_true', help='Apply device-specific HAL patch')
    group.add_argument('--revert', action='store_true', help='Revert only this HAL patch')
    args = parser.parse_args()
    path = args.tree / 'hardware/qcom/thermal/thermalConfig.cpp'
    if not path.is_file():
        parser.error(f'Missing QTI Thermal HAL source: {path}')
    source = path.read_text(encoding='utf-8')
    try:
        updated = revert_patch(source) if args.revert else apply_patch(source)
    except PatchError as error:
        parser.error(str(error))
    if args.check:
        print('mondrian A17 thermal HAL: valid; patch ' +
              ('present' if START_MARKER in source else 'not applied'))
        return 0
    if updated == source:
        print('mondrian A17 thermal HAL: no change required')
        return 0
    atomic_write(path, updated)
    print(f'mondrian A17 thermal HAL: {"reverted" if args.revert else "applied"} {path}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
