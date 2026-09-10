#
# Copyright (C) 2022-2023 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from xiaomi sm8450-common
TARGET_HAS_UDFPS := true
$(call inherit-product, device/xiaomi/sm8450-common/common.mk)

# Inherit from the proprietary version
$(call inherit-product, vendor/xiaomi/mondrian/mondrian-vendor.mk)

# Audio
PRODUCT_PACKAGES += \
    firmware_aw_cali.bin_symlink

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/audio/mixer_paths_waipio_mtp.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio/sku_cape/mixer_paths_waipio_mtp.xml \
    $(LOCAL_PATH)/audio/resourcemanager_waipio_mtp.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio/sku_cape/resourcemanager_waipio_mtp.xml \
    $(LOCAL_PATH)/audio/usecaseKvManager.xml:$(TARGET_COPY_OUT_VENDOR)/etc/usecaseKvManager.xml

# Overlay
PRODUCT_PACKAGES += \
    FrameworksResMondrian \
    FrameworksResMondrianGlobal \
    LineageResMondrian \
    NfcResMondrian \
    SettingsProviderResMondrian \
    SettingsProviderResMondrianCN \
    SettingsResMondrian \
    SystemUIResMondrian \
    WifiResMondrian \
    WifiResMondrianCN

# Soong namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)

# System properties
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/properties/build_CN.prop:$(TARGET_COPY_OUT_ODM)/etc/build_CN.prop \
    $(LOCAL_PATH)/properties/build_GL.prop:$(TARGET_COPY_OUT_ODM)/etc/build_GL.prop

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/properties/build_CN.prop:$(TARGET_COPY_OUT_RECOVERY)/root/vendor/odm/etc/build_CN.prop \
    $(LOCAL_PATH)/properties/build_GL.prop:$(TARGET_COPY_OUT_RECOVERY)/root/vendor/odm/etc/build_GL.prop

# Vibrator
PRODUCT_COPY_FILES += \
    vendor/xiaomi/mondrian/proprietary/vendor/etc/vibrator/primitive_effect_0.bin:$(TARGET_COPY_OUT_VENDOR)/etc/vibrator/primitive_effect_0.bin \
    vendor/xiaomi/mondrian/proprietary/vendor/etc/vibrator/primitive_effect_1.bin:$(TARGET_COPY_OUT_VENDOR)/etc/vibrator/primitive_effect_1.bin \
    vendor/xiaomi/mondrian/proprietary/vendor/etc/vibrator/primitive_effect_2.bin:$(TARGET_COPY_OUT_VENDOR)/etc/vibrator/primitive_effect_2.bin \
    vendor/xiaomi/mondrian/proprietary/vendor/etc/vibrator/primitive_effect_3.bin:$(TARGET_COPY_OUT_VENDOR)/etc/vibrator/primitive_effect_3.bin \
    vendor/xiaomi/mondrian/proprietary/vendor/etc/vibrator/primitive_effect_4.bin:$(TARGET_COPY_OUT_VENDOR)/etc/vibrator/primitive_effect_4.bin \
    vendor/xiaomi/mondrian/proprietary/vendor/etc/vibrator/primitive_effect_5.bin:$(TARGET_COPY_OUT_VENDOR)/etc/vibrator/primitive_effect_5.bin \
    vendor/xiaomi/mondrian/proprietary/vendor/etc/vibrator/primitive_effect_6.bin:$(TARGET_COPY_OUT_VENDOR)/etc/vibrator/primitive_effect_6.bin \
    vendor/xiaomi/mondrian/proprietary/vendor/etc/vibrator/primitive_effect_7.bin:$(TARGET_COPY_OUT_VENDOR)/etc/vibrator/primitive_effect_7.bin \
    vendor/xiaomi/mondrian/proprietary/vendor/etc/vibrator/primitive_effect_8.bin:$(TARGET_COPY_OUT_VENDOR)/etc/vibrator/primitive_effect_8.bin \
    vendor/xiaomi/mondrian/proprietary/vendor/etc/vibrator/primitive_effect_9.bin:$(TARGET_COPY_OUT_VENDOR)/etc/vibrator/primitive_effect_9.bin

$(call soong_config_set,qti_vibrator,effect_lib,libqtivibratoreffect.xiaomi)
$(call soong_config_set_bool,qti_vibrator,use_effect_stream,true)

# Include MIUI Camera
$(call inherit-product-if-exists, device/xiaomi/miuicamera-cupid/device.mk)
