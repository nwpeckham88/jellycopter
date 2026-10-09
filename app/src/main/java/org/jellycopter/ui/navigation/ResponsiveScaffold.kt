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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import org.jellycopter.ui.components.FocusCard
import org.jellycopter.ui.components.JellycopterLogo
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Back key navigation: from any screen back to Home
    BackHandler(enabled = currentScreen != NavScreen.HOME) {
        onScreenSelected(NavScreen.HOME)
    }

    when (deviceType) {
        DeviceType.TV -> TvLeanbackLayout(
            currentScreen = currentScreen,
            onScreenSelected = onScreenSelected,
            isDiscoverEnabled = isDiscoverEnabled,
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
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

    var railHasFocus by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(
        targetValue = if (railHasFocus) 220.dp else 76.dp,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "railWidth"
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(JellycopterTheme.colors.background)
    ) {
        // Vertical Frosted Left Navigation Rail (Expandable on remote focus)
        Column(
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(JellycopterTheme.colors.surface.copy(alpha = 0.96f))
                .border(BorderStroke(1.dp, Color(0x14FFFFFF)))
                .onFocusChanged { railHasFocus = it.hasFocus }
                .padding(vertical = 18.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Brand emblem at top (anchored horizontally so it never jumps on expand/collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x16FFFFFF))
                            .border(
                                BorderStroke(1.dp, Color(0x24FFFFFF)),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        JellycopterLogo(
                            modifier = Modifier.size(22.dp),
                            tint = JellycopterTheme.colors.primaryAccent
                        )
                    }
                }

                if (railWidth > 120.dp) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = "JELLYCOPTER",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            color = JellycopterTheme.colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Media Lounge",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.4.sp,
                            color = JellycopterTheme.colors.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search action item
            TvRailItem(
                icon = Icons.Rounded.Search,
                label = "Search",
                isSelected = false,
                railExpanded = railWidth > 120.dp,
                onClick = { onScreenSelected(NavScreen.LIBRARY) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Navigation Screen Items
            NavScreen.topLevelScreens(isDiscoverEnabled).forEach { screen ->
                TvRailItem(
                    icon = screen.icon,
                    label = screen.title,
                    isSelected = screen == currentScreen,
                    railExpanded = railWidth > 120.dp,
                    onClick = { onScreenSelected(screen) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // User profile avatar item
            TvRailItem(
                icon = Icons.Rounded.Person,
                label = "User Profile",
                subtitle = "Connected",
                isSelected = false,
                railExpanded = railWidth > 120.dp,
                onClick = { onScreenSelected(NavScreen.SETTINGS) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Settings item
            TvRailItem(
                icon = Icons.Rounded.Settings,
                label = "Settings",
                isSelected = currentScreen == NavScreen.SETTINGS,
                railExpanded = railWidth > 120.dp,
                onClick = { onScreenSelected(NavScreen.SETTINGS) }
            )
        }

        // Content Area with TV overscan margins and floating status header
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Main Screen Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, end = 36.dp, top = 20.dp, bottom = 20.dp)
            ) {
                content()
            }

            // Top-right floating frosted status capsule (Brand + Real-time Clock)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 36.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x1AFFFFFF))
                    .border(
                        BorderStroke(1.dp, Color(0x22FFFFFF)),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JellycopterLogo(
                    modifier = Modifier.size(15.dp),
                    tint = JellycopterTheme.colors.primaryAccent
                )
                Text(
                    text = "JELLYCOPTER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.1.sp,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(Color(0x55FFFFFF))
                )
                Text(
                    text = currentTime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                    color = Color(0xFFCBD5E1)
                )
            }
        }
    }
}

/**
 * Modern TV Navigation Rail Item:
 * - Borderless & backgroundless when idle/unfocused
 * - High-contrast crisp focus pill with clean focus border and smooth scale
 * - Sleek vertical accent indicator for active route
 * - Rock-solid stationary icon position during rail expansion/collapse
 */
@Composable
private fun TvRailItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    railExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val shape = RoundedCornerShape(10.dp)

    val iconScale by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1.0f,
        animationSpec = tween(150),
        label = "railIconScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(
                brush = when {
                    isFocused -> SolidColor(Color(0x2AFFFFFF))
                    isSelected -> SolidColor(JellycopterTheme.colors.primaryAccent.copy(alpha = 0.14f))
                    else -> SolidColor(Color.Transparent)
                },
                shape = shape
            )
            .border(
                BorderStroke(
                    width = if (isFocused) 1.5.dp else if (isSelected) 1.dp else 0.dp,
                    color = when {
                        isFocused -> Color.White
                        isSelected -> JellycopterTheme.colors.primaryAccent.copy(alpha = 0.30f)
                        else -> Color.Transparent
                    }
                ),
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Active route vertical pill indicator on left edge
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 3.dp)
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (isFocused) Color.White else JellycopterTheme.colors.primaryAccent)
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stationary icon slot (anchored at start, width 48dp, center 24dp)
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                val tint = when {
                    isFocused -> Color.White
                    isSelected -> JellycopterTheme.colors.primaryAccent
                    else -> Color(0xFF94A3B8)
                }
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier
                        .size(24.dp)
                        .scale(iconScale),
                    tint = tint
                )
            }

            if (railExpanded) {
                Spacer(modifier = Modifier.width(6.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.5.sp,
                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            isFocused -> Color.White
                            isSelected -> JellycopterTheme.colors.primaryAccent
                            else -> Color(0xFFE2E8F0)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (isFocused) Color.White.copy(alpha = 0.8f) else Color(0xFF10B981),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
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
