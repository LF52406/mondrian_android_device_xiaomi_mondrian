# Copyright (C) 2026 The Android Open Source Project
# SPDX-License-Identifier: Apache-2.0

# Framework preparation runs before Soong consumes product configuration.
# Set this to false before inheriting device.mk to omit the feature completely.
MONDRIAN_HAPTIC_ENGINE ?= true
ifeq ($(MONDRIAN_HAPTIC_ENGINE),true)
_mondrian_haptic_patch := $(shell python3 device/xiaomi/mondrian/haptic-settings/apply-framework-patch.py --rom-root "$(abspath .)" 2>&1)
ifneq ($(_mondrian_haptic_patch),ok)
$(error Mondrian Haptic Engine integration failed: $(_mondrian_haptic_patch))
endif
PRODUCT_PACKAGES += \
    MondrianHapticSettings \
    MondrianHapticEngineOverlay
endif
