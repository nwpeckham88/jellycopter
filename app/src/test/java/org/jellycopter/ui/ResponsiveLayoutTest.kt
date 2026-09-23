package org.jellycopter.ui

import org.jellycopter.ui.navigation.DeviceType
import org.jellycopter.ui.navigation.NavScreen
import org.jellycopter.ui.navigation.ResponsiveBreakpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutTest {

    @Test
    fun testTvResolutionWhenLeanbackFeaturePresent() {
        // TV hardware flag overrides width
        val tv1 = ResponsiveBreakpoints.resolveDeviceType(isTvFeaturePresent = true, screenWidthDp = 1920)
        assertEquals(DeviceType.TV, tv1)

        val tv2 = ResponsiveBreakpoints.resolveDeviceType(isTvFeaturePresent = true, screenWidthDp = 1280)
        assertEquals(DeviceType.TV, tv2)

        val tv3 = ResponsiveBreakpoints.resolveDeviceType(isTvFeaturePresent = true, screenWidthDp = 500)
        assertEquals(DeviceType.TV, tv3)
    }

    @Test
    fun testPhoneResolutionForCompactWidth() {
        // < 600dp width is Phone
        val phone1 = ResponsiveBreakpoints.resolveDeviceType(isTvFeaturePresent = false, screenWidthDp = 360)
        assertEquals(DeviceType.PHONE, phone1)

        val phone2 = ResponsiveBreakpoints.resolveDeviceType(isTvFeaturePresent = false, screenWidthDp = 412)
        assertEquals(DeviceType.PHONE, phone2)

        val phoneBoundary = ResponsiveBreakpoints.resolveDeviceType(isTvFeaturePresent = false, screenWidthDp = 599)
        assertEquals(DeviceType.PHONE, phoneBoundary)
    }

    @Test
    fun testTabletResolutionForExpandedWidth() {
        // >= 600dp width is Tablet
        val tablet1 = ResponsiveBreakpoints.resolveDeviceType(isTvFeaturePresent = false, screenWidthDp = 600)
        assertEquals(DeviceType.TABLET, tablet1)

        val tablet2 = ResponsiveBreakpoints.resolveDeviceType(isTvFeaturePresent = false, screenWidthDp = 840)
        assertEquals(DeviceType.TABLET, tablet2)

        val tablet3 = ResponsiveBreakpoints.resolveDeviceType(isTvFeaturePresent = false, screenWidthDp = 1200)
        assertEquals(DeviceType.TABLET, tablet3)
    }

    @Test
    fun testNavRoutesStructure() {
        val topScreens = NavScreen.topLevelScreens
        assertEquals(5, topScreens.size)
        assertTrue(topScreens.contains(NavScreen.HOME))
        assertTrue(topScreens.contains(NavScreen.CHANNELS))
        assertTrue(topScreens.contains(NavScreen.DISCOVER))
        assertTrue(topScreens.contains(NavScreen.LIBRARY))
        assertTrue(topScreens.contains(NavScreen.TROPHY_ROOM))

        // PostPlayback is modal / session end, not a top-level tab
        assertFalse(topScreens.contains(NavScreen.POST_PLAYBACK))
        assertFalse(NavScreen.POST_PLAYBACK.isTopLevel)

        // Settings is modal / dedicated action, not in default top-level tabs
        assertFalse(topScreens.contains(NavScreen.SETTINGS))
        assertFalse(NavScreen.SETTINGS.isTopLevel)
    }

    @Test
    fun testDiscoverTabDecoupling() {
        val enabledScreens = NavScreen.topLevelScreens(isDiscoverEnabled = true)
        assertEquals(5, enabledScreens.size)
        assertTrue(enabledScreens.contains(NavScreen.DISCOVER))

        val disabledScreens = NavScreen.topLevelScreens(isDiscoverEnabled = false)
        assertEquals(4, disabledScreens.size)
        assertFalse(disabledScreens.contains(NavScreen.DISCOVER))
        assertTrue(disabledScreens.contains(NavScreen.HOME))
        assertTrue(disabledScreens.contains(NavScreen.CHANNELS))
        assertTrue(disabledScreens.contains(NavScreen.LIBRARY))
        assertTrue(disabledScreens.contains(NavScreen.TROPHY_ROOM))
    }

    @Test
    fun testAllNavScreensHaveImageVectorIcons() {
        NavScreen.entries.forEach { screen ->
            org.junit.Assert.assertNotNull("NavScreen ${screen.name} must have a vector icon", screen.icon)
        }
    }

    @Test
    fun test3StateFocusTinting() {
        val primaryAccent = androidx.compose.ui.graphics.Color(0xFFAA5CC3)
        val idleColor = androidx.compose.ui.graphics.Color(0xFF94A3B8)
        val focusedColor = androidx.compose.ui.graphics.Color.White

        // 1. Idle (neither focused nor selected) -> muted gray
        val idle = org.jellycopter.ui.navigation.navItemIconTint(
            isFocused = false,
            isSelected = false,
            primaryAccent = primaryAccent
        )
        assertEquals(idleColor, idle)

        // 2. Focused (focused but not selected) -> pure white
        val focused = org.jellycopter.ui.navigation.navItemIconTint(
            isFocused = true,
            isSelected = false,
            primaryAccent = primaryAccent
        )
        assertEquals(focusedColor, focused)

        // 3. Selected (selected but not focused) -> primary accent
        val selected = org.jellycopter.ui.navigation.navItemIconTint(
            isFocused = false,
            isSelected = true,
            primaryAccent = primaryAccent
        )
        assertEquals(primaryAccent, selected)

        // 4. Focused & Selected (focused takes precedence for D-pad feedback) -> pure white
        val focusedSelected = org.jellycopter.ui.navigation.navItemIconTint(
            isFocused = true,
            isSelected = true,
            primaryAccent = primaryAccent
        )
        assertEquals(focusedColor, focusedSelected)
    }
}
