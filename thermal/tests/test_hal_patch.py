#!/usr/bin/env python3
"""Host-only tests for the optional mondrian QTI Thermal HAL patch."""

from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from apply_thermal_hal_patch import (
    INCLUDE, START_MARKER, PatchError, apply_patch, revert_patch,
)


SOURCE = '''#include <android-base/logging.h>
#include "thermalData.h"
std::vector<struct target_therm_cfg> waipio_specific = {
    { TemperatureType::SKIN, { "xo-therm" } },
};
void ThermalConfig::configure() {
        thermalConfig = add_target_config(soc_id, hwPlatform, limitp, it->second);
}
'''


class HalPatchTest(unittest.TestCase):
    def test_apply_only_once_and_keep_original_config(self):
        modified = apply_patch(SOURCE)
        self.assertEqual(modified.count(START_MARKER), 1)
        self.assertEqual(modified.count('"quiet_therm"'), 1)
        self.assertIn('soc_id == 530', modified)
        self.assertIn('ro.product.device', modified)
        self.assertIn('sensor.sensor_list[0] == "xo-therm"', modified)
        self.assertEqual(apply_patch(modified), modified)

    def test_revert_is_exact_for_supported_original(self):
        self.assertEqual(revert_patch(apply_patch(SOURCE)), SOURCE)

    def test_does_not_remove_upstream_properties_include(self):
        source = SOURCE.replace('#include <android-base/logging.h>\n',
                                '#include <android-base/logging.h>\n#include <android-base/properties.h>\n')
        self.assertNotIn(INCLUDE, apply_patch(source))
        self.assertEqual(revert_patch(apply_patch(source)), source)

    def test_fail_closed_when_hal_source_does_not_match(self):
        with self.assertRaises(PatchError):
            apply_patch(SOURCE.replace('waipio_specific', 'wrong_platform'))
        with self.assertRaises(PatchError):
            apply_patch(SOURCE.replace('add_target_config(', 'changed_symbol('))
        with self.assertRaises(PatchError):
            revert_patch(apply_patch(SOURCE).replace('MONDRIAN_SKIN_A17_END',
                                                    'BROKEN_END_MARKER'))
        with self.assertRaises(PatchError):
            revert_patch(apply_patch(SOURCE).replace('quiet_therm',
                                                    'other_sensor'))


if __name__ == '__main__':
    unittest.main()
