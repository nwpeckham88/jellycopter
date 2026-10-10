package org.jellycopter.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import org.jellycopter.core.database.ChannelEntity
import org.jellycopter.ui.components.AnalogStaticSnow
import org.jellycopter.ui.components.AnalogVuMeter
import org.jellycopter.ui.components.BroadcastSlateCard
import org.jellycopter.ui.components.CrtScreenTube
import org.jellycopter.ui.components.FocusCard
import org.jellycopter.ui.components.JellycopterLogo
import org.jellycopter.ui.components.RetroAudioEffects
import org.jellycopter.ui.components.RotaryChannelDial
import org.jellycopter.ui.components.SpeakerGrille
import org.jellycopter.ui.components.TactilePushButton
import org.jellycopter.ui.components.VfdChannelDisplay
import org.jellycopter.ui.theme.JellycopterTheme

/**
 * Resolves 3-state navigation icon tinting:
 * - Idle: Color(0xFF94A3B8) (muted gray)
 * - Focused: Color.White
 * - Selected: primaryAccent
 */
fun navItemIconTint(isFocused: Boolean, isSelected: Boolean, primaryAccent: Color): Color = when {
    isFocused -> Color.White
    isSelected -> primaryAccent
    else -> Color(0xFF94A3B8)
}

@Composable
fun navItemIconTint(isFocused: Boolean, isSelected: Boolean): Color {
    return navItemIconTint(isFocused, isSelected, JellycopterTheme.colors.primaryAccent)
}

@Composable
fun ResponsiveScaffold(
    deviceType: DeviceType,
    currentScreen: NavScreen,
    onScreenSelected: (NavScreen) -> Unit,
    isDiscoverEnabled: Boolean = true,
    liveChannels: List<ChannelEntity> = emptyList(),
    onTuneSmartChannel: ((ChannelEntity) -> Unit)? = null,
    onCreateSmartChannelAt: ((Int) -> Unit)? = null,
    currentTvChannel: Int = 2,
    onTvChannelChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Back key navigation for mobile/tablet: from any screen back to Home
    BackHandler(enabled = deviceType != DeviceType.TV && currentScreen != NavScreen.HOME) {
        onScreenSelected(NavScreen.HOME)
    }

    when (deviceType) {
        DeviceType.TV -> TvLeanbackLayout(
            currentScreen = currentScreen,
            onScreenSelected = onScreenSelected,
            isDiscoverEnabled = isDiscoverEnabled,
            liveChannels = liveChannels,
            onTuneSmartChannel = onTuneSmartChannel,
            onCreateSmartChannelAt = onCreateSmartChannelAt,
            currentChannel = currentTvChannel,
            onCurrentChannelChanged = onTvChannelChanged,
            modifier = modifier,
            content = content
        )
        DeviceType.TABLET -> TabletRailLayout(
            currentScreen = currentScreen,
            onScreenSelected = onScreenSelected,
            isDiscoverEnabled = isDiscoverEnabled,
            modifier = modifier,
            content = content
        )
        DeviceType.PHONE -> MobileBottomNavLayout(
            currentScreen = currentScreen,
            onScreenSelected = onScreenSelected,
            isDiscoverEnabled = isDiscoverEnabled,
            modifier = modifier,
            content = content
        )
    }
}

@Composable
private fun TvLeanbackLayout(
    currentScreen: NavScreen,
    onScreenSelected: (NavScreen) -> Unit,
    isDiscoverEnabled: Boolean,
    liveChannels: List<ChannelEntity>,
    onTuneSmartChannel: ((ChannelEntity) -> Unit)?,
    onCreateSmartChannelAt: ((Int) -> Unit)?,
    currentChannel: Int = 2,
    onCurrentChannelChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("jellycopter_settings", Context.MODE_PRIVATE)
    }
    val scanlinesEnabled = prefs.getBoolean("key_crt_scanlines", true)

    var currentTime by remember {
        mutableStateOf(
            try {
                java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
            } catch (_: Exception) {
                "12:00 PM"
            }
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            currentTime = try {
                java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
            } catch (_: Exception) {
                "12:00 PM"
            }
        }
    }

    fun getChannelLabel(channel: Int): String {
        if (currentScreen != NavScreen.HOME) {
            return when (currentScreen) {
                NavScreen.LIBRARY -> "AUX • LIBRARY"
                NavScreen.DISCOVER -> "AUX • DISCOVER"
                NavScreen.TROPHY_ROOM -> "AUX • TROPHIES"
                NavScreen.SETTINGS -> "AUX • SETTINGS"
                NavScreen.CHANNELS -> "AUX • CHANNELS"
                NavScreen.POST_PLAYBACK -> "AUX • STATS"
                else -> "AUX INPUT"
            }
        }
        return when (channel) {
            2 -> "TV GUIDE"
            else -> {
                val sc = liveChannels.firstOrNull { it.channelNumber == channel }
                sc?.name?.uppercase() ?: "NO SIGNAL"
            }
        }
    }

    var isFocusInNavWing by remember { mutableStateOf(false) }
    val crtScreenFocusRequester = remember { FocusRequester() }
    val guideFocusRequester = remember { FocusRequester() }
    val libraryFocusRequester = remember { FocusRequester() }
    val channelsFocusRequester = remember { FocusRequester() }
    val trophiesFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val dialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(300L)
        try {
            guideFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    fun changeChannel(newChannel: Int) {
        val normalized = when {
            newChannel < 2 -> 13
            newChannel > 13 -> 2
            else -> newChannel
        }
        RetroAudioEffects.playKnobClick()
        onCurrentChannelChanged(normalized)
        if (currentScreen != NavScreen.HOME) {
            onScreenSelected(NavScreen.HOME)
        }
    }

    // Android TV Leanback Back-button routing:
    // 1. Browsing content on Home Channel 2 -> hop focus to tuner navigation wing
    BackHandler(enabled = currentScreen == NavScreen.HOME && currentChannel == 2 && !isFocusInNavWing) {
        try {
            guideFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // 2. Watching a broadcast channel on Home -> tune back to Channel 2 (Guide) and focus wing
    BackHandler(enabled = currentScreen == NavScreen.HOME && currentChannel != 2) {
        changeChannel(2)
        try {
            guideFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // 3. Sub-screens (Library, Settings, Discover, Trophies) -> return to Home Channel 2 and focus wing
    BackHandler(enabled = currentScreen != NavScreen.HOME) {
        onScreenSelected(NavScreen.HOME)
        changeChannel(2)
        try {
            guideFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Outer frame: 16:9 walnut woodgrain cabinet with brass pinstripe border
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF22150D), // Walnut cabinet woodgrain
                        Color(0xFF160E08),
                        Color(0xFF28180E),
                        Color(0xFF19100A),
                        Color(0xFF24160D),
                        Color(0xFF140D07)
                    )
                )
            )
            .border(
                BorderStroke(
                    width = 3.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFE5C07B), // Antique brass
                            Color(0xFFB8860B),
                            Color(0xFFD4AF37),
                            Color(0xFF8B6914)
                        )
                    )
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(10.dp)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    when {
                        keyEvent.key == Key.ChannelUp || keyCode == KeyEvent.KEYCODE_CHANNEL_UP ||
                        keyEvent.key == Key.PageUp || keyCode == KeyEvent.KEYCODE_PAGE_UP -> {
                            val next = if (currentChannel >= 13) 2 else currentChannel + 1
                            changeChannel(next)
                            true
                        }
                        keyEvent.key == Key.ChannelDown || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN ||
                        keyEvent.key == Key.PageDown || keyCode == KeyEvent.KEYCODE_PAGE_DOWN -> {
                            val prev = if (currentChannel <= 2) 13 else currentChannel - 1
                            changeChannel(prev)
                            true
                        }
                        keyEvent.key == Key.Two || keyEvent.key == Key.NumPad2 || keyCode == KeyEvent.KEYCODE_2 || keyCode == KeyEvent.KEYCODE_NUMPAD_2 -> {
                            changeChannel(2)
                            true
                        }
                        keyEvent.key == Key.Three || keyEvent.key == Key.NumPad3 || keyCode == KeyEvent.KEYCODE_3 || keyCode == KeyEvent.KEYCODE_NUMPAD_3 -> {
                            changeChannel(3)
                            true
                        }
                        keyEvent.key == Key.Four || keyEvent.key == Key.NumPad4 || keyCode == KeyEvent.KEYCODE_4 || keyCode == KeyEvent.KEYCODE_NUMPAD_4 -> {
                            changeChannel(4)
                            true
                        }
                        keyEvent.key == Key.Five || keyEvent.key == Key.NumPad5 || keyCode == KeyEvent.KEYCODE_5 || keyCode == KeyEvent.KEYCODE_NUMPAD_5 -> {
                            changeChannel(5)
                            true
                        }
                        keyEvent.key == Key.Six || keyEvent.key == Key.NumPad6 || keyCode == KeyEvent.KEYCODE_6 || keyCode == KeyEvent.KEYCODE_NUMPAD_6 -> {
                            changeChannel(6)
                            true
                        }
                        keyEvent.key == Key.Seven || keyEvent.key == Key.NumPad7 || keyCode == KeyEvent.KEYCODE_7 || keyCode == KeyEvent.KEYCODE_NUMPAD_7 -> {
                            changeChannel(7)
                            true
                        }
                        keyEvent.key == Key.Eight || keyEvent.key == Key.NumPad8 || keyCode == KeyEvent.KEYCODE_8 || keyCode == KeyEvent.KEYCODE_NUMPAD_8 -> {
                            changeChannel(8)
                            true
                        }
                        keyEvent.key == Key.Nine || keyEvent.key == Key.NumPad9 || keyCode == KeyEvent.KEYCODE_9 || keyCode == KeyEvent.KEYCODE_NUMPAD_9 -> {
                            changeChannel(9)
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Inner cabinet chassis with brass pinstripe inlay
        Row(
            modifier = Modifier
                .fillMaxSize()
                .border(BorderStroke(1.dp, Color(0x55D4AF37)), RoundedCornerShape(10.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Center-Left (~72% width): The 4:3 CRT Picture Tube
            Box(
                modifier = Modifier
                    .weight(0.72f)
                    .fillMaxHeight()
                    .focusGroup()
                    .focusRequester(crtScreenFocusRequester)
                    .focusProperties {
                        right = when (currentScreen) {
                            NavScreen.HOME -> guideFocusRequester
                            NavScreen.LIBRARY -> libraryFocusRequester
                            NavScreen.CHANNELS -> channelsFocusRequester
                            NavScreen.TROPHY_ROOM -> trophiesFocusRequester
                            NavScreen.SETTINGS -> settingsFocusRequester
                            else -> guideFocusRequester
                        }
                    }
            ) {
                CrtScreenTube(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 24.dp,
                    scanlinesEnabled = scanlinesEnabled
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Green/amber phosphor On-Screen Display (OSD) top bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xCC050E08),
                                            Color(0x66050E08),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (currentScreen != NavScreen.HOME) "AUX" else "CH ${if (currentChannel < 10) "0$currentChannel" else "$currentChannel"}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF34D399) // Phosphor green
                                )
                                Text(
                                    text = getChannelLabel(currentChannel),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF6EE7B7)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "STEREO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFFFB300) // Amber phosphor glow
                                )
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF34D399))
                                )
                                Text(
                                    text = currentTime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }

                        // Content inside the CRT tube: Channel 2 is TV Guide / Home
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                        ) {
                            if (currentScreen != NavScreen.HOME) {
                                content()
                            } else if (currentChannel == 2) {
                                content()
                            } else {
                                val smartChannel = liveChannels.firstOrNull { it.channelNumber == currentChannel }
                                if (smartChannel != null) {
                                    SmartChannelBroadcastView(
                                        channel = smartChannel,
                                        onTune = { onTuneSmartChannel?.invoke(smartChannel) },
                                        scanlinesEnabled = scanlinesEnabled
                                    )
                                } else {
                                    AnalogStaticSnow(
                                        isUnprogrammed = true,
                                        channelNumber = currentChannel,
                                        onProgramChannel = { onCreateSmartChannelAt?.invoke(currentChannel) },
                                        promptText = "[PRESS ENTER TO PROGRAM CHANNEL $currentChannel]"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Right Wing (~28% width): The Physical TV Hardware Control Wing
            Column(
                modifier = Modifier
                    .weight(0.28f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF20150D), // Mahogany / brass trim panel
                                Color(0xFF170E08),
                                Color(0xFF120A05)
                            )
                        )
                    )
                    .border(BorderStroke(1.5.dp, Color(0xFF8B6914)), RoundedCornerShape(10.dp))
                    .onFocusChanged { isFocusInNavWing = it.hasFocus }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Rotary VHF Channel Dial (numbered 2..13, animated rotation on channel change)
                RotaryChannelDial(
                    selectedChannel = currentChannel,
                    onChannelSelected = { changeChannel(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(dialFocusRequester)
                        .focusProperties {
                            left = crtScreenFocusRequester
                            down = guideFocusRequester
                        },
                    dialSize = 58.dp
                )

                // Analog Backlit VU Meter + Glowing Green VFD Channel Display ('CH 02 GUIDE', etc.)
                AnalogVuMeter(
                    modifier = Modifier.fillMaxWidth(),
                    meterLabel = "AUDIO MODULATION VU"
                )

                VfdChannelDisplay(
                    channelNumber = if (currentScreen != NavScreen.HOME) 0 else currentChannel,
                    channelName = getChannelLabel(currentChannel),
                    modifier = Modifier.fillMaxWidth()
                )

                // Mechanical Push Buttons for shortcuts:
                // '[CH 2] GUIDE', '[LIBRARY]', '[DISCOVER]', '[TROPHIES]', '[SETTINGS]'
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    TactilePushButton(
                        label = "[CH 2] GUIDE",
                        isSelected = currentScreen == NavScreen.HOME && currentChannel == 2,
                        onClick = {
                            changeChannel(2)
                            onScreenSelected(NavScreen.HOME)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(guideFocusRequester)
                            .focusProperties {
                                left = crtScreenFocusRequester
                                up = dialFocusRequester
                                down = libraryFocusRequester
                            },
                        badgeText = "02"
                    )
                    TactilePushButton(
                        label = "[LIBRARY]",
                        isSelected = currentScreen == NavScreen.LIBRARY,
                        onClick = { onScreenSelected(NavScreen.LIBRARY) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(libraryFocusRequester)
                            .focusProperties {
                                left = crtScreenFocusRequester
                                up = guideFocusRequester
                                down = channelsFocusRequester
                            },
                        badgeText = "AUX"
                    )
                    TactilePushButton(
                        label = "[CHANNELS]",
                        isSelected = currentScreen == NavScreen.CHANNELS,
                        onClick = { onScreenSelected(NavScreen.CHANNELS) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(channelsFocusRequester)
                            .focusProperties {
                                left = crtScreenFocusRequester
                                up = libraryFocusRequester
                                down = trophiesFocusRequester
                            },
                        badgeText = "AUX"
                    )
                    TactilePushButton(
                        label = "[TROPHIES]",
                        isSelected = currentScreen == NavScreen.TROPHY_ROOM,
                        onClick = { onScreenSelected(NavScreen.TROPHY_ROOM) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(trophiesFocusRequester)
                            .focusProperties {
                                left = crtScreenFocusRequester
                                up = channelsFocusRequester
                                down = settingsFocusRequester
                            },
                        badgeText = "AUX"
                    )
                    TactilePushButton(
                        label = "[SETTINGS]",
                        isSelected = currentScreen == NavScreen.SETTINGS,
                        onClick = { onScreenSelected(NavScreen.SETTINGS) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(settingsFocusRequester)
                            .focusProperties {
                                left = crtScreenFocusRequester
                                up = trophiesFocusRequester
                            },
                        badgeText = "AUX"
                    )
                }

                // Speaker grille with brass 'SOLID STATE STEREO' badge
                SpeakerGrille(
                    modifier = Modifier.fillMaxWidth(),
                    slatCount = 4,
                    badgeText = "SOLID STATE STEREO"
                )
            }
        }
    }
}

@Composable
private fun SmartChannelBroadcastView(
    channel: ChannelEntity,
    onTune: () -> Unit,
    scanlinesEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var hasAutoLaunched by remember(channel.channelId) { mutableStateOf(false) }

    LaunchedEffect(channel.channelId) {
        RetroAudioEffects.playStaticBurst()
        delay(2000L)
        if (!hasAutoLaunched) {
            hasAutoLaunched = true
            onTune()
        }
    }

    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    RetroAudioEffects.playKnobClick()
                    onTune()
                }
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            RetroAudioEffects.playKnobClick()
                            onTune()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        val chNumStr = if (channel.channelNumber < 10) "0${channel.channelNumber}" else "${channel.channelNumber}"
        BroadcastSlateCard(
            modifier = Modifier.fillMaxSize(),
            stationCallsign = "[WJEL-TV CH $chNumStr]",
            channelTitle = channel.name.uppercase(),
            showTitle = channel.description?.takeIf { it.isNotBlank() } ?: "BROADCAST TRANSMISSION ACQUIRED",
            statusText = if (hasAutoLaunched) "BROADCAST READY • PRESS ENTER TO WATCH" else "TUNING SIGNAL • PLEASE STAND BY",
            scanlinesEnabled = scanlinesEnabled
        )

        // Overlay action pill at bottom center
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xE60A0E14))
                .border(
                    BorderStroke(
                        width = if (isFocused) 2.dp else 1.5.dp,
                        color = if (isFocused) Color(0xFFFFD54F) else Color(0xFF34D399)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "[PRESS ENTER TO WATCH LIVE]",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                color = if (isFocused) Color(0xFFFFD54F) else Color(0xFF34D399)
            )
        }
    }
}

@Composable
private fun TabletRailLayout(
    currentScreen: NavScreen,
    onScreenSelected: (NavScreen) -> Unit,
    isDiscoverEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(JellycopterTheme.colors.background)
    ) {
        NavigationRail(
            containerColor = JellycopterTheme.colors.surface,
            contentColor = Color.White,
            modifier = Modifier.fillMaxHeight()
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            JellycopterLogo(
                modifier = Modifier
                    .size(28.dp)
                    .padding(bottom = 8.dp),
                tint = JellycopterTheme.colors.primaryAccent
            )

            NavScreen.topLevelScreens(isDiscoverEnabled).forEach { screen ->
                val isSelected = screen == currentScreen
                NavigationRailItem(
                    selected = isSelected,
                    onClick = { onScreenSelected(screen) },
                    icon = {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text(screen.title, fontSize = 11.sp) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = JellycopterTheme.colors.primaryAccent,
                        selectedTextColor = JellycopterTheme.colors.primaryAccent,
                        indicatorColor = JellycopterTheme.colors.surfaceVariant,
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8)
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Tablet Settings item
            NavigationRailItem(
                selected = currentScreen == NavScreen.SETTINGS,
                onClick = { onScreenSelected(NavScreen.SETTINGS) },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = { Text("Settings", fontSize = 11.sp) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = JellycopterTheme.colors.primaryAccent,
                    selectedTextColor = JellycopterTheme.colors.primaryAccent,
                    indicatorColor = JellycopterTheme.colors.surfaceVariant,
                    unselectedIconColor = Color(0xFF94A3B8),
                    unselectedTextColor = Color(0xFF94A3B8)
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun MobileBottomNavLayout(
    currentScreen: NavScreen,
    onScreenSelected: (NavScreen) -> Unit,
    isDiscoverEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JellycopterTheme.colors.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JellycopterTheme.colors.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JellycopterLogo(
                        modifier = Modifier.size(24.dp),
                        tint = JellycopterTheme.colors.primaryAccent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Jellycopter",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                FocusCard(
                    onClick = { onScreenSelected(NavScreen.SETTINGS) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(36.dp)
                ) { isFocused ->
                    val isSettingsSelected = currentScreen == NavScreen.SETTINGS
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                when {
                                    isFocused -> JellycopterTheme.colors.surfaceVariant
                                    isSettingsSelected -> JellycopterTheme.colors.primaryAccent.copy(alpha = 0.25f)
                                    else -> Color.Transparent
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(22.dp),
                            tint = navItemIconTint(isFocused = isFocused, isSelected = isSettingsSelected)
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = JellycopterTheme.colors.surface,
                contentColor = Color.White
            ) {
                NavScreen.topLevelScreens(isDiscoverEnabled).forEach { screen ->
                    val isSelected = screen == currentScreen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onScreenSelected(screen) },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text(screen.title, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JellycopterTheme.colors.primaryAccent,
                            selectedTextColor = JellycopterTheme.colors.primaryAccent,
                            indicatorColor = JellycopterTheme.colors.surfaceVariant,
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            content()
        }
    }
}
