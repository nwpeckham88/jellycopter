package org.jellycopter.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Nightlife
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellycopter.core.repository.AuthRepository
import org.jellycopter.core.settings.SettingsRepository
import org.jellycopter.sync.PluginSyncService
import org.jellycopter.ui.components.FocusCard
import org.jellycopter.ui.theme.BaseThemeMode
import org.jellycopter.ui.theme.JellycopterTheme
import org.jellycopter.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository? = null,
    authRepository: AuthRepository? = null,
    pluginSyncService: PluginSyncService? = null,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel? = null
) {
    val vm = viewModel ?: remember(settingsRepository, authRepository, pluginSyncService) {
        SettingsViewModel(settingsRepository, authRepository, pluginSyncService)
    }
    val focusManager = LocalFocusManager.current
    val session = remember(authRepository) { authRepository?.getSession() }

    // Theme state
    val currentTheme = settingsRepository?.themeModeFlow?.collectAsState()?.value
        ?: settingsRepository?.getThemeMode()
        ?: BaseThemeMode.WARM_DARK

    // Audio DSP settings
    val audioDsp = settingsRepository?.audioDspSettingsFlow?.collectAsState()?.value
        ?: settingsRepository?.getAudioDspSettings()

    var drcEnabled by remember(audioDsp) { mutableStateOf(audioDsp?.nightModeDrcEnabled ?: false) }
    var forceStereoEnabled by remember(audioDsp) { mutableStateOf(audioDsp?.forceStereoEnabled ?: false) }
    var centerBoostDb by remember(audioDsp) { mutableFloatStateOf(audioDsp?.centerBoostDb ?: 3.0f) }

    // Subtitle settings
    val subtitleSettings = settingsRepository?.subtitleSettingsFlow?.collectAsState()?.value
        ?: settingsRepository?.getSubtitleSettings()
    var subtitleDimmerEnabled by remember(subtitleSettings) { mutableStateOf(subtitleSettings?.subtitleDimmerEnabled ?: false) }
    var subtitleDimRatio by remember(subtitleSettings) { mutableFloatStateOf(subtitleSettings?.subtitleDimRatio ?: 0.5f) }

    // Auto Night settings
    val autoNightSettings = settingsRepository?.autoNightSettingsFlow?.collectAsState()?.value
        ?: settingsRepository?.getAutoNightSettings()
    var autoNightEnabled by remember(autoNightSettings) { mutableStateOf(autoNightSettings?.autoNightModeEnabled ?: false) }
    var quietStartText by remember(autoNightSettings) { mutableStateOf(autoNightSettings?.quietHoursStart ?: "22:30") }
    var quietEndText by remember(autoNightSettings) { mutableStateOf(autoNightSettings?.quietHoursEnd ?: "07:00") }
    var quietHoursStatus by remember { mutableStateOf<String?>(null) }

    // Jellyseerr URL
    val initialJellyseerr = settingsRepository?.getJellyseerrUrl() ?: ""
    var jellyseerrUrlText by remember { mutableStateOf(initialJellyseerr) }
    var jellyseerrSaveStatus by remember { mutableStateOf<String?>(null) }

    // Plugin Sync Probe Status from ViewModel
    val pluginStatus = vm.pluginStatus
    val isProbingPlugin = vm.isProbingPlugin

    fun checkPlugin() {
        vm.probePlugin(session?.serverUrl)
    }

    LaunchedEffect(Unit) {
        checkPlugin()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(JellycopterTheme.colors.background),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 48.dp)
    ) {
        // Screen Header
        item {
            Column {
                Text(
                    text = "PREFERENCES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = JellycopterTheme.colors.primaryAccent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Settings & Configuration",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Manage server connection, themes, audio DSP processing, and integrations",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // Section 1: Server Details & Account
        item {
            SettingsCard(
                title = "Server Connection & Account",
                icon = Icons.Rounded.Dns
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = session?.serverName ?: "Jellyfin Server",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = session?.serverUrl ?: "http://localhost:8096",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x2510B981))
                                .border(BorderStroke(1.dp, Color(0x5010B981)), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Connected",
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF34D399)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Connected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6EE7B7)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "User",
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFFCBD5E1)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Signed in as ${session?.username ?: "Guest"}",
                                fontSize = 13.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }

                        FocusCard(
                            onClick = {
                                vm.logout(onComplete = onSignOut)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isFocused) Color(0xFFEF4444)
                                        else Color(0x30EF4444)
                                    )
                                    .border(
                                        BorderStroke(1.dp, Color(0x60EF4444)),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                                        contentDescription = "Sign Out",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isFocused) Color.White else Color(0xFFFCA5A5)
                                    )
                                    Text(
                                        text = "Sign Out",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFocused) Color.White else Color(0xFFFCA5A5)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Theme Selector
        item {
            SettingsCard(
                title = "Visual Theme",
                icon = Icons.Rounded.Palette
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Select your preferred visual aesthetic:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    val themes = listOf(
                        Triple(BaseThemeMode.WARM_DARK, "Warm Amber Dark", "Rich espresso backdrop with glowing golden amber accents, warm candlelight atmosphere, and terracotta highlights"),
                        Triple(BaseThemeMode.JELLYFIN_PURPLE, "Jellyfin Purple", "Deep navy backdrop with signature Jellyfin violet and cyan accents"),
                        Triple(BaseThemeMode.OLED_PURE_BLACK, "OLED Pure Black", "True 0-nit blacks optimized for OLED panels with crisp electric blue accents"),
                        Triple(BaseThemeMode.CYBERPUNK_NEON, "Cyberpunk Neon", "High-contrast dark obsidian canvas with vibrant cyan and hot magenta neon glow")
                    )

                    themes.forEach { (mode, name, desc) ->
                        val isSelected = currentTheme == mode
                        FocusCard(
                            onClick = { settingsRepository?.setThemeMode(mode) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            isFocused -> JellycopterTheme.colors.primaryAccent.copy(alpha = 0.35f)
                                            isSelected -> JellycopterTheme.colors.primaryAccent.copy(alpha = 0.18f)
                                            else -> Color(0x15FFFFFF)
                                        }
                                    )
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isSelected) JellycopterTheme.colors.primaryAccent else Color(0x20FFFFFF)
                                        ),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = desc,
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8),
                                            maxLines = 2
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            modifier = Modifier.size(20.dp),
                                            tint = JellycopterTheme.colors.primaryAccent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Audio DSP Defaults
        item {
            SettingsCard(
                title = "Audio DSP & Night Mode Defaults",
                icon = Icons.AutoMirrored.Rounded.VolumeUp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // DRC Toggle Card
                    SettingsToggleCard(
                        title = "Night Mode DRC (Dynamic Range Compression)",
                        description = "Boosts whisper/dialogue quiet passages and clamps sudden loud explosive transients",
                        checked = drcEnabled,
                        onCheckedChange = {
                            drcEnabled = it
                            settingsRepository?.setNightModeDrcEnabled(it)
                        }
                    )

                    // Force Stereo Toggle Card
                    SettingsToggleCard(
                        title = "ITU-R BS.775 Force Stereo Downmixing",
                        description = "Downmixes 5.1/7.1 surround sound to 2.0 stereo with dialogue center channel enhancement",
                        checked = forceStereoEnabled,
                        onCheckedChange = {
                            forceStereoEnabled = it
                            settingsRepository?.setForceStereoEnabled(it)
                        }
                    )

                    // Center Dialogue Boost D-pad Slider Card
                    SettingsSliderCard(
                        title = "Center Dialogue Boost",
                        valueText = String.format("+%.1f dB", centerBoostDb),
                        value = centerBoostDb,
                        valueRange = 0.0f..6.0f,
                        stepSize = 0.5f,
                        steps = 11,
                        onValueChange = {
                            centerBoostDb = it
                            settingsRepository?.setCenterBoostDb(it)
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Auto Night Mode Scheduled Quiet Hours Toggle Card
                    SettingsToggleCard(
                        title = "Auto Night Mode Quiet Hours",
                        description = "Automatically activates Dynamic Range Compression during scheduled night hours (crosses midnight)",
                        checked = autoNightEnabled,
                        onCheckedChange = {
                            autoNightEnabled = it
                            settingsRepository?.setAutoNightModeEnabled(it)
                        }
                    )

                    if (autoNightEnabled) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x18FFFFFF))
                                .border(BorderStroke(1.dp, Color(0x25FFFFFF)), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Scheduled Quiet Hours Window (HH:mm format, 24-hour):",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = quietStartText,
                                    onValueChange = {
                                        quietStartText = it
                                        quietHoursStatus = null
                                    },
                                    label = { Text("Start Time") },
                                    placeholder = { Text("22:30") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = JellycopterTheme.colors.primaryAccent,
                                        unfocusedBorderColor = Color(0x35FFFFFF),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color(0xFFE2E8F0)
                                    )
                                )

                                OutlinedTextField(
                                    value = quietEndText,
                                    onValueChange = {
                                        quietEndText = it
                                        quietHoursStatus = null
                                    },
                                    label = { Text("End Time") },
                                    placeholder = { Text("07:00") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = JellycopterTheme.colors.primaryAccent,
                                        unfocusedBorderColor = Color(0x35FFFFFF),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color(0xFFE2E8F0)
                                    )
                                )

                                FocusCard(
                                    onClick = {
                                        val timeRegex = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
                                        val cleanStart = quietStartText.trim()
                                        val cleanEnd = quietEndText.trim()
                                        if (timeRegex.matches(cleanStart) && timeRegex.matches(cleanEnd)) {
                                            settingsRepository?.setQuietHours(cleanStart, cleanEnd)
                                            quietHoursStatus = "Quiet hours set: $cleanStart to $cleanEnd"
                                        } else {
                                            quietHoursStatus = "Invalid format. Please use HH:mm (e.g. 22:30)"
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) { isFocused ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isFocused) Color.White else JellycopterTheme.colors.primaryAccent)
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Set Hours",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFocused) Color.Black else Color.White
                                        )
                                    }
                                }
                            }

                            if (quietHoursStatus != null) {
                                Text(
                                    text = quietHoursStatus ?: "",
                                    fontSize = 11.sp,
                                    color = if (quietHoursStatus?.startsWith("Invalid") == true) Color(0xFFF87171) else Color(0xFF34D399),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Subtitle Dimming & HDR Glare Reduction
        item {
            SettingsCard(
                title = "Subtitle Dimming & HDR Glare Reduction",
                icon = Icons.Rounded.Subtitles
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Subtitle Dimmer Toggle Card
                    SettingsToggleCard(
                        title = "HDR Subtitle Luminance Dimmer",
                        description = "Attenuates caption luminance to prevent blinding eye strain on HDR10 and Dolby Vision displays in dark rooms",
                        checked = subtitleDimmerEnabled,
                        onCheckedChange = {
                            subtitleDimmerEnabled = it
                            settingsRepository?.setSubtitleDimmerEnabled(it)
                        }
                    )

                    // Luminance Attenuation D-pad Slider Card
                    SettingsSliderCard(
                        title = "Luminance Attenuation Level",
                        valueText = String.format("%.0f%% Dimmed", subtitleDimRatio * 100),
                        value = subtitleDimRatio,
                        valueRange = 0.10f..0.90f,
                        stepSize = 0.10f,
                        steps = 7,
                        onValueChange = {
                            subtitleDimRatio = it
                            settingsRepository?.setSubtitleDimRatio(it)
                        }
                    )

                    // Visual Preview Swatch for TV 10-foot UI
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(BorderStroke(1.dp, Color(0x30FFFFFF)), RoundedCornerShape(8.dp))
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val factor = if (subtitleDimmerEnabled) (1.0f - subtitleDimRatio) else 1.0f
                        val textColor = Color(
                            red = factor,
                            green = factor,
                            blue = factor,
                            alpha = 1.0f
                        )
                        Text(
                            text = if (subtitleDimmerEnabled) "Preview: Attenuated Subtitle Caption" else "Preview: Standard 100% White Subtitle",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        )
                    }
                }
            }
        }

        // Section 5: Silent Companion Plugin Sync Detection
        item {
            SettingsCard(
                title = "Jellyfin Companion Plugin Sync",
                icon = Icons.Rounded.Sync
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Silent probe status for /Jellycopter/Ping endpoint. Syncs exhaustion decks & trophies across devices:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x20FFFFFF))
                            .border(BorderStroke(1.dp, Color(0x30FFFFFF)), RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pluginStatus,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (pluginStatus.contains("Connected")) Color(0xFF34D399) else Color(0xFFCBD5E1)
                        )

                        FocusCard(
                            onClick = { checkPlugin() },
                            shape = RoundedCornerShape(8.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isFocused) Color.White else JellycopterTheme.colors.surfaceVariant)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Probe Plugin",
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isFocused) Color.Black else Color.White
                                    )
                                    Text(
                                        text = "Re-check",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFocused) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 6: Jellyseerr Integration & Discover Tab Configuration
        item {
            SettingsCard(
                title = "Jellyseerr Integration (Discover Tab)",
                icon = Icons.Rounded.Explore
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter your Jellyseerr base URL to enable the Discover tab for browsing trending carousels and issuing 1-Click requests. If left empty, the Discover tab is cleanly hidden.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    OutlinedTextField(
                        value = jellyseerrUrlText,
                        onValueChange = {
                            jellyseerrUrlText = it
                            jellyseerrSaveStatus = null
                        },
                        label = { Text("Jellyseerr Base URL") },
                        placeholder = { Text("http://192.168.1.100:5055") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Explore,
                                contentDescription = "Jellyseerr",
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFF94A3B8)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JellycopterTheme.colors.primaryAccent,
                            unfocusedBorderColor = Color(0x35FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (jellyseerrSaveStatus != null) {
                        Text(
                            text = jellyseerrSaveStatus ?: "",
                            fontSize = 12.sp,
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FocusCard(
                            onClick = {
                                val cleanUrl = jellyseerrUrlText.trim().ifEmpty { null }
                                settingsRepository?.setJellyseerrUrl(cleanUrl)
                                jellyseerrSaveStatus = if (cleanUrl != null) "Saved! Discover tab is enabled." else "Jellyseerr disabled. Discover tab is hidden."
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isFocused) Color.White else JellycopterTheme.colors.primaryAccent)
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Save Configuration",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFocused) Color.Black else Color.White
                                )
                            }
                        }

                        FocusCard(
                            onClick = {
                                jellyseerrUrlText = ""
                                settingsRepository?.setJellyseerrUrl(null)
                                jellyseerrSaveStatus = "Jellyseerr disabled. Discover tab is hidden."
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isFocused) Color.White else Color(0x25FFFFFF))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Clear / Disable",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFocused) Color.Black else Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JellycopterTheme.colors.surface)
            .border(
                BorderStroke(1.dp, Color(0x18FFFFFF)),
                RoundedCornerShape(12.dp)
            )
            .padding(18.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(JellycopterTheme.colors.primaryAccent.copy(alpha = 0.15f))
                        .border(1.dp, JellycopterTheme.colors.primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = JellycopterTheme.colors.primaryAccent
                    )
                }
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            content()
        }
    }
}

@Composable
private fun SettingsToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    FocusCard(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) JellycopterTheme.colors.surfaceVariant else Color(0x0EFFFFFF))
                .border(
                    BorderStroke(
                        1.dp,
                        if (isFocused) Color(0x35FFFFFF) else Color(0x12FFFFFF)
                    ),
                    RoundedCornerShape(8.dp)
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = JellycopterTheme.colors.primaryAccent,
                    uncheckedThumbColor = Color(0xFF94A3B8),
                    uncheckedTrackColor = Color(0x25FFFFFF)
                )
            )
        }
    }
}

@Composable
private fun SettingsSliderCard(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    stepSize: Float,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    FocusCard(
        onClick = {},
        modifier = Modifier
            .fillMaxWidth()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            val newVal = (value - stepSize).coerceIn(valueRange.start, valueRange.endInclusive)
                            onValueChange(newVal)
                            true
                        }
                        Key.DirectionRight -> {
                            val newVal = (value + stepSize).coerceIn(valueRange.start, valueRange.endInclusive)
                            onValueChange(newVal)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        shape = RoundedCornerShape(8.dp)
    ) { isFocused ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) JellycopterTheme.colors.surfaceVariant else Color(0x0EFFFFFF))
                .border(
                    BorderStroke(
                        1.dp,
                        if (isFocused) Color(0x35FFFFFF) else Color(0x12FFFFFF)
                    ),
                    RoundedCornerShape(8.dp)
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isFocused) {
                        Text(
                            text = "◄ ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = JellycopterTheme.colors.primaryAccent
                        )
                    }
                    Text(
                        text = valueText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = JellycopterTheme.colors.primaryAccent
                    )
                    if (isFocused) {
                        Text(
                            text = " ►",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = JellycopterTheme.colors.primaryAccent
                        )
                    }
                }
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = JellycopterTheme.colors.primaryAccent
                )
            )
        }
    }
}

