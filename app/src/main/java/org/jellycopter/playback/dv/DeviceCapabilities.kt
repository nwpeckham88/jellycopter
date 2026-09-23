package org.jellycopter.playback.dv

import android.os.Build

/**
 * Device display and hardware video decoder capability descriptor.
 */
data class DeviceCapabilities(
    val supportsDolbyVision: Boolean = false,
    val supportsDvProfile81: Boolean = false,
    val supportsDvProfile7: Boolean = false,
    val supportsHdr10: Boolean = true,
    val deviceModel: String = Build.MODEL ?: "Unknown",
    val deviceManufacturer: String = Build.MANUFACTURER ?: "Unknown"
) {
    /**
     * Nvidia Shield TV (Tegra X1 / X1+) and Onn 4K Pro (Amlogic S905X4)
     * support single-layer Profile 8.1 and Profile 5, but lack dual-layer Profile 7 decoding.
     */
    val isShieldTvOrOnn4kPro: Boolean
        get() = isShieldTv(deviceModel) || isOnn4kPro(deviceModel)

    companion object {
        fun isShieldTv(model: String): Boolean {
            val m = model.lowercase()
            return m.contains("shield") || m.contains("darcy") || m.contains("foster") || m.contains("mdarcy")
        }

        fun isOnn4kPro(model: String): Boolean {
            val m = model.lowercase()
            return m.contains("onn") && (m.contains("pro") || m.contains("4k"))
        }

        /**
         * Standard profile for Nvidia Shield TV.
         */
        fun createShieldTv(): DeviceCapabilities {
            return DeviceCapabilities(
                supportsDolbyVision = true,
                supportsDvProfile81 = true,
                supportsDvProfile7 = false,
                supportsHdr10 = true,
                deviceModel = "SHIELD Android TV",
                deviceManufacturer = "NVIDIA"
            )
        }

        /**
         * Standard profile for Onn 4K Pro.
         */
        fun createOnn4kPro(): DeviceCapabilities {
            return DeviceCapabilities(
                supportsDolbyVision = true,
                supportsDvProfile81 = true,
                supportsDvProfile7 = false,
                supportsHdr10 = true,
                deviceModel = "onn. 4K Pro",
                deviceManufacturer = "Walmart"
            )
        }

        /**
         * Standard profile for SDR or non-DV HDR10 displays.
         */
        fun createNonDvHdr10(): DeviceCapabilities {
            return DeviceCapabilities(
                supportsDolbyVision = false,
                supportsDvProfile81 = false,
                supportsDvProfile7 = false,
                supportsHdr10 = true,
                deviceModel = "Standard HDR10 TV",
                deviceManufacturer = "Generic"
            )
        }
    }
}
