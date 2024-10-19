#
# Copyright (C) 2024 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit some common PixelProject stuff.
$(call inherit-product, vendor/aosp/config/common_full_phone.mk)

# Inherit from vermeer device
$(call inherit-product, device/xiaomi/vermeer/device.mk)

# PixelProject Flags
CUSTOM_BUILD_TYPE := Unofficial
TARGET_FACE_UNLOCK_SUPPORTED := true
USE_PIXEL_CHARGER := true
TARGET_INCLUDE_LIVE_WALLPAPERS := true
TARGET_SUPPORTS_QUICK_TAP  := true
PRODUCT_NO_CAMERA := true
TARGET_SUPPORTS_GOOGLE_BATTERY := false
TARGET_SUPPORTS_CLEAR_CALLING := true

PRODUCT_DEVICE := vermeer
PRODUCT_NAME := aosp_vermeer
PRODUCT_BRAND := POCO
PRODUCT_MODEL := 23113RKC6G
PRODUCT_MANUFACTURER := xiaomi

PRODUCT_GMS_CLIENTID_BASE := android-xiaomi

# Boot Animation
TARGET_BOOT_ANIMATION_RES := 1440

PRODUCT_BUILD_PROP_OVERRIDES += \
    PRIVATE_BUILD_DESC="vermeer_global-user 14 UKQ1.230804.001 V816.0.6.0.UNKMIXM release-keys"

BUILD_FINGERPRINT := POCO/vermeer_global/vermeer:14/UKQ1.230804.001/V816.0.6.0.UNKMIXM:user/release-keys
