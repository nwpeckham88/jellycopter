package org.jellycopter.ui.navigation

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

enum class DeviceType {
    TV,
    PHONE,
    TABLET
}

object ResponsiveBreakpoints {
    const val COMPACT_MAX_WIDTH_DP = 599
    const val TABLET_MIN_WIDTH_DP = 600
    const val EXPANDED_MIN_WIDTH_DP = 840

    fun resolveDeviceType(isTvFeaturePresent: Boolean, screenWidthDp: Int): DeviceType {
        return when {
            isTvFeaturePresent -> DeviceType.TV
            screenWidthDp >= TABLET_MIN_WIDTH_DP -> DeviceType.TABLET
            else -> DeviceType.PHONE
        }
    }
}

@Composable
fun rememberDeviceType(): DeviceType {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    return remember(screenWidth) {
        val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        ResponsiveBreakpoints.resolveDeviceType(isTv, screenWidth)
    }
}
