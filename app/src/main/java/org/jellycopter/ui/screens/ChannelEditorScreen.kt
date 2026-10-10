package org.jellycopter.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellycopter.core.database.ChannelEntity
import org.jellycopter.core.model.SeriesDetail
import org.jellycopter.ui.components.FocusCard
import org.jellycopter.ui.theme.JellycopterTheme
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellycopter.core.repository.MediaRepository
import org.jellycopter.ui.components.StationPreviewArt
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip

/**
 * Channel create/edit screen.
 *
 * Allows the user to:
 * - Name and describe a channel
 * - Add/remove shows from a picker
 * - Choose interleave strategy (Round-Robin / Random / Weighted)
 * - Configure night mode, skip behavior, resume behavior
 * - Save or delete the channel
 *
 * Uses [ChannelEntity] as the backing model persisted via ChannelRepository.
 */
@Composable
fun ChannelEditorScreen(
    existingChannel: ChannelEntity? = null,
    availableSeries: List<SeriesDetail> = emptyList(),
    mediaRepository: MediaRepository? = null,
    initialSeriesIds: List<String> = emptyList(),
    initialChannelNumber: Int? = null,
    existingChannels: List<ChannelEntity> = emptyList(),
    onSave: (ChannelEntity) -> Unit = {},
    onDelete: ((String) -> Unit)? = null,
    onCancel: () -> Unit = {}
) {
    // Intercept hardware/remote back press to cleanly cancel and exit editor
    BackHandler(onBack = onCancel)

    val isEditing = existingChannel != null

    val defaultChannelNumber = remember(existingChannel, initialChannelNumber, existingChannels) {
        when {
            existingChannel != null -> existingChannel.channelNumber.coerceAtLeast(3)
            initialChannelNumber != null -> initialChannelNumber.coerceAtLeast(3)
            existingChannels.isNotEmpty() -> {
                val used = existingChannels.map { it.channelNumber }.toSet()
                var cand = 3
                while (cand in used) {
                    cand++
                }
                cand
            }
            else -> 3
        }
    }

    var channelName by remember { mutableStateOf(existingChannel?.name ?: "") }
    var channelNumber by remember { mutableStateOf(defaultChannelNumber) }
    var channelNumberText by remember { mutableStateOf(defaultChannelNumber.toString()) }
    var channelDescription by remember { mutableStateOf(existingChannel?.description ?: "") }
    var interleaveStrategy by remember { mutableStateOf(existingChannel?.interleaveStrategy ?: "ROUND_ROBIN") }
    var audioNightMode by remember { mutableStateOf(existingChannel?.audioNightMode ?: "USE_GLOBAL") }
    var skipBehavior by remember { mutableStateOf(existingChannel?.skipBehavior ?: "RECYCLED") }
    var resumeBehavior by remember { mutableStateOf(existingChannel?.resumeBehavior ?: "NEXT_EPISODE") }

    // Deserialize existing showsJson to a list of series IDs or initialize with pre-seeded series
    val selectedSeriesIds = remember {
        mutableStateListOf<String>().apply {
            if (existingChannel?.showsJson != null) {
                val ids = runCatching {
                    kotlinx.serialization.json.Json.decodeFromString<List<String>>(existingChannel.showsJson)
                }.getOrDefault(emptyList())
                addAll(ids)
            } else if (initialSeriesIds.isNotEmpty()) {
                addAll(initialSeriesIds)
            }
        }
    }

    var showInterleavePicker by remember { mutableStateOf(false) }
    var showNightModePicker by remember { mutableStateOf(false) }
    var showSkipPicker by remember { mutableStateOf(false) }
    var showResumePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSeriesPicker by remember { mutableStateOf(false) }
    var seriesSearchQuery by remember { mutableStateOf("") }
    var isEditingName by remember { mutableStateOf(false) }
    val nameFocusRequester = remember { FocusRequester() }
    var isEditingNumber by remember { mutableStateOf(false) }
    val numberFocusRequester = remember { FocusRequester() }
    var isEditingDescription by remember { mutableStateOf(false) }
    val descriptionFocusRequester = remember { FocusRequester() }

    val focusManager = LocalFocusManager.current
    val accent = JellycopterTheme.colors.primaryAccent
    val surface = JellycopterTheme.colors.surface
    val surfaceVariant = JellycopterTheme.colors.surfaceVariant
    val textPrimary = JellycopterTheme.colors.onBackground
    val textSecondary = JellycopterTheme.colors.onSurface.copy(alpha = 0.7f)

    val defaultName = if (channelNumber >= 3) "Channel $channelNumber" else "Channel 3"
    val effectiveName = channelName.trim().ifBlank { defaultName }
    val canSave = selectedSeriesIds.isNotEmpty()

    val doSave: () -> Unit = {
        if (canSave) {
            val resolvedChannelNumber = (channelNumberText.toIntOrNull() ?: channelNumber).coerceAtLeast(3)
            val finalName = channelName.trim().ifBlank { "Channel $resolvedChannelNumber" }
            val showsJson = Json.encodeToString(selectedSeriesIds.toList())
            val entity = ChannelEntity(
                channelId = existingChannel?.channelId ?: UUID.randomUUID().toString(),
                name = finalName,
                description = channelDescription.trim().ifBlank { null },
                showsJson = showsJson,
                channelNumber = resolvedChannelNumber,
                interleaveStrategy = interleaveStrategy,
                audioNightMode = audioNightMode,
                skipBehavior = skipBehavior,
                resumeBehavior = resumeBehavior,
                createdAt = existingChannel?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            onSave(entity)
        }
    }

    // Option Picker TV Dialog
    if (showInterleavePicker) {
        OptionPickerDialog(
            title = "Select Interleave Strategy",
            options = listOf(
                "ROUND_ROBIN" to "Round-Robin (Rotate evenly through series)",
                "RANDOM" to "Random (Completely randomized shuffle)",
                "WEIGHTED" to "Weighted (Favor less frequently played shows)"
            ),
            selectedOption = interleaveStrategy,
            onOptionSelected = {
                interleaveStrategy = it
                showInterleavePicker = false
            },
            onDismiss = { showInterleavePicker = false }
        )
    }

    if (showNightModePicker) {
        OptionPickerDialog(
            title = "Select Audio Night Mode",
            options = listOf(
                "USE_GLOBAL" to "Use Global Setting",
                "ON" to "Always On (Compress dynamic range & boost dialog)",
                "OFF" to "Always Off"
            ),
            selectedOption = audioNightMode,
            onOptionSelected = {
                audioNightMode = it
                showNightModePicker = false
            },
            onDismiss = { showNightModePicker = false }
        )
    }

    if (showSkipPicker) {
        OptionPickerDialog(
            title = "Select Skip Behavior",
            options = listOf(
                "RECYCLED" to "Recycled (Return skipped episode back to pool)",
                "COUNTED" to "Counted (Mark skipped episode as played)"
            ),
            selectedOption = skipBehavior,
            onOptionSelected = {
                skipBehavior = it
                showSkipPicker = false
            },
            onDismiss = { showSkipPicker = false }
        )
    }

    if (showResumePicker) {
        OptionPickerDialog(
            title = "Select Resume On Return",
            options = listOf(
                "NEXT_EPISODE" to "Next Episode",
                "BEGINNING" to "Beginning of Current Episode",
                "EXACT_POSITION" to "Exact Position"
            ),
            selectedOption = resumeBehavior,
            onOptionSelected = {
                resumeBehavior = it
                showResumePicker = false
            },
            onDismiss = { showResumePicker = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JellycopterTheme.colors.background)
            .padding(horizontal = 24.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with title and quick action buttons (Cancel & Save)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "VIRTUAL BROADCAST",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isEditing) "Edit Channel" else "Create Channel",
                        color = textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Build your custom virtual TV station",
                        color = textSecondary,
                        fontSize = 13.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Cancel Button
                    FocusCard(
                        onClick = onCancel,
                        shape = RoundedCornerShape(8.dp)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.08f))
                                .border(1.dp, if (isFocused) Color.White else Color(0x18FFFFFF), RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                color = if (isFocused) Color.Black else textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Header Save / Create Button
                    FocusCard(
                        onClick = doSave,
                        shape = RoundedCornerShape(8.dp)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .background(
                                    when {
                                        !canSave -> surfaceVariant.copy(alpha = 0.5f)
                                        isFocused -> Color.White
                                        else -> accent
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (isFocused) Color.White else Color(0x20FFFFFF),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isEditing) Icons.Rounded.Check else Icons.Rounded.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (isFocused) Color.Black else if (canSave) Color.White else textSecondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEditing) "Save Changes" else "Create Channel",
                                    color = if (isFocused) Color.Black else if (canSave) Color.White else textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Channel name field (click to edit on TV)
        item {
            if (isEditingName) {
                LaunchedEffect(Unit) {
                    try { nameFocusRequester.requestFocus() } catch (_: Exception) {}
                }
                OutlinedTextField(
                    value = channelName,
                    onValueChange = { channelName = it },
                    label = { Text("Channel Name", color = accent) },
                    placeholder = { Text("e.g. Night 'Toons", color = textSecondary.copy(alpha = 0.5f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        isEditingName = false
                        focusManager.clearFocus()
                    }),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color(0x20FFFFFF),
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        cursorColor = accent
                    ),
                    singleLine = true
                )
            } else {
                FocusCard(
                    onClick = { isEditingName = true },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { isFocused ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isFocused) accent.copy(alpha = 0.18f) else surfaceVariant.copy(alpha = 0.4f))
                            .border(
                                width = 1.dp,
                                color = if (isFocused) accent else Color(0x20FFFFFF),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CHANNEL NAME",
                                color = if (isFocused) accent else textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = channelName.ifBlank { "Click to set name (e.g. Night 'Toons)" },
                                color = if (channelName.isBlank()) textSecondary.copy(alpha = 0.6f) else textPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (channelName.isBlank()) FontWeight.Normal else FontWeight.Medium
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit Channel Name",
                            tint = if (isFocused) accent else textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Channel number selector
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CHANNEL NUMBER",
                            color = textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "TV dial position (Channel 3 or higher)",
                            color = textSecondary.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "CH $channelNumber",
                        color = accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Decrement button
                    FocusCard(
                        onClick = {
                            if (channelNumber > 3) {
                                channelNumber--
                                channelNumberText = channelNumber.toString()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(width = 56.dp, height = 48.dp)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    when {
                                        channelNumber <= 3 -> surface.copy(alpha = 0.4f)
                                        isFocused -> accent
                                        else -> surface
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "Decrease channel number",
                                tint = when {
                                    channelNumber <= 3 -> textSecondary.copy(alpha = 0.3f)
                                    isFocused -> Color.Black
                                    else -> textPrimary
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Number input (click to edit on TV, or use +/- buttons)
                    if (isEditingNumber) {
                        LaunchedEffect(Unit) {
                            try { numberFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                        OutlinedTextField(
                            value = channelNumberText,
                            onValueChange = { input ->
                                val digits = input.filter { it.isDigit() }
                                channelNumberText = digits
                                digits.toIntOrNull()?.let { num ->
                                    if (num >= 3) {
                                        channelNumber = num
                                    }
                                }
                            },
                            label = { Text("Channel #", color = accent) },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(numberFocusRequester),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val parsed = channelNumberText.toIntOrNull() ?: channelNumber
                                    val clamped = parsed.coerceAtLeast(3)
                                    channelNumber = clamped
                                    channelNumberText = clamped.toString()
                                    isEditingNumber = false
                                    focusManager.clearFocus()
                                }
                            ),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                unfocusedBorderColor = Color(0x20FFFFFF),
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary,
                                cursorColor = accent
                            ),
                            singleLine = true
                        )
                    } else {
                        FocusCard(
                            onClick = { isEditingNumber = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (isFocused) accent.copy(alpha = 0.18f) else surface)
                                    .border(
                                        width = 1.dp,
                                        color = if (isFocused) accent else Color(0x20FFFFFF),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Channel $channelNumber",
                                    color = if (isFocused) accent else textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Increment button
                    FocusCard(
                        onClick = {
                            channelNumber++
                            channelNumberText = channelNumber.toString()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(width = 56.dp, height = 48.dp)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (isFocused) accent else surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Increase channel number",
                                tint = if (isFocused) Color.Black else textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Optional description (click to edit on TV)
        item {
            if (isEditingDescription) {
                LaunchedEffect(Unit) {
                    try { descriptionFocusRequester.requestFocus() } catch (_: Exception) {}
                }
                OutlinedTextField(
                    value = channelDescription,
                    onValueChange = { channelDescription = it },
                    label = { Text("Description (optional)", color = accent) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(descriptionFocusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        isEditingDescription = false
                        focusManager.clearFocus()
                    }),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color(0x20FFFFFF),
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        cursorColor = accent
                    ),
                    maxLines = 2
                )
            } else {
                FocusCard(
                    onClick = { isEditingDescription = true },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { isFocused ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isFocused) accent.copy(alpha = 0.18f) else surfaceVariant.copy(alpha = 0.4f))
                            .border(
                                width = 1.dp,
                                color = if (isFocused) accent else Color(0x20FFFFFF),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DESCRIPTION (OPTIONAL)",
                                color = if (isFocused) accent else textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = channelDescription.ifBlank { "Click to add channel description" },
                                color = if (channelDescription.isBlank()) textSecondary.copy(alpha = 0.6f) else textPrimary,
                                fontSize = 13.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit Description",
                            tint = if (isFocused) accent else textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Live Station Preview
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "STATION PREVIEW",
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(surfaceVariant)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val previewShowsJson = kotlinx.serialization.json.Json.encodeToString(selectedSeriesIds.toList())
                    StationPreviewArt(
                        showsJson = previewShowsJson,
                        mediaRepository = mediaRepository,
                        modifier = Modifier.size(width = 120.dp, height = 68.dp),
                        fallbackTitle = channelName.ifBlank { "Station" }
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CH $channelNumber",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                modifier = Modifier
                                    .background(accent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = channelName.ifBlank { "New Station" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (selectedSeriesIds.isEmpty()) "No shows added yet"
                            else "${selectedSeriesIds.size} show${if (selectedSeriesIds.size == 1) "" else "s"} • ${interleaveStrategy.toReadable()}",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }
                }
            }
        }

        // Shows Checklist section
        item {
            val countText = "${selectedSeriesIds.size} of ${availableSeries.size} included"
            SectionHeader(
                title = "Channel Lineup ($countText)",
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Select shows below to include in this broadcast channel. Press enter on any show to toggle.",
                color = textSecondary,
                fontSize = 12.sp
            )
        }

        // Search / Filter row (Click-to-activate so keyboard doesn't auto popup when scrolling)
        item {
            var isSearchActive by remember { mutableStateOf(false) }
            val searchFocusRequester = remember { FocusRequester() }

            if (isSearchActive) {
                LaunchedEffect(Unit) {
                    try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
                }
                OutlinedTextField(
                    value = seriesSearchQuery,
                    onValueChange = { seriesSearchQuery = it },
                    label = { Text("Filter Shows", color = accent) },
                    placeholder = { Text("Search by show title...", color = textSecondary) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = accent.copy(alpha = 0.5f),
                        focusedLabelColor = accent,
                        unfocusedLabelColor = textSecondary,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        cursorColor = accent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        isSearchActive = false
                        focusManager.clearFocus()
                    })
                )
            } else {
                FocusCard(
                    onClick = { isSearchActive = true },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { isFocused ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isFocused) accent.copy(alpha = 0.18f) else surface)
                            .border(
                                width = 1.dp,
                                color = if (isFocused) accent else Color(0x20FFFFFF),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = if (isFocused) accent else textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (seriesSearchQuery.isBlank()) "Filter shows (Click to type)..." else "Filter: \"$seriesSearchQuery\"",
                                color = if (seriesSearchQuery.isBlank()) textSecondary else textPrimary,
                                fontSize = 13.sp
                            )
                        }
                        if (seriesSearchQuery.isNotBlank()) {
                            Text(
                                text = "Clear",
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { seriesSearchQuery = "" }
                            )
                        }
                    }
                }
            }
        }

        // Quick Batch Actions (Select All Filtered / Deselect All)
        item {
            val query = seriesSearchQuery.trim()
            val filteredSeries = if (query.isBlank()) {
                availableSeries
            } else {
                availableSeries.filter { it.name.contains(query, ignoreCase = true) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Select All (filtered)
                FocusCard(
                    onClick = {
                        val idsToAdd = filteredSeries.map { it.id }.filter { it !in selectedSeriesIds }
                        selectedSeriesIds.addAll(idsToAdd)
                    },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) { isFocused ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isFocused) Color.White.copy(alpha = 0.15f) else surface)
                            .border(1.dp, if (isFocused) accent else Color(0x18FFFFFF), RoundedCornerShape(6.dp))
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Select All${if (query.isNotBlank()) " Filtered" else ""}",
                            fontSize = 12.sp,
                            color = if (isFocused) accent else textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Deselect All (filtered)
                FocusCard(
                    onClick = {
                        val idsToRemove = filteredSeries.map { it.id }
                        selectedSeriesIds.removeAll(idsToRemove)
                    },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) { isFocused ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isFocused) Color.White.copy(alpha = 0.15f) else surface)
                            .border(1.dp, if (isFocused) accent else Color(0x18FFFFFF), RoundedCornerShape(6.dp))
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Deselect All",
                            fontSize = 12.sp,
                            color = if (isFocused) Color(0xFFEF4444) else textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        val query = seriesSearchQuery.trim()
        val filteredSeries = if (query.isBlank()) {
            availableSeries
        } else {
            availableSeries.filter { it.name.contains(query, ignoreCase = true) }
        }

        if (filteredSeries.isEmpty()) {
            item {
                Text(
                    text = if (availableSeries.isEmpty()) {
                        "No TV series found in library."
                    } else {
                        "No TV series found matching \"$seriesSearchQuery\"."
                    },
                    color = textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        } else {
            items(filteredSeries, key = { "check_${it.id}" }) { series ->
                val isSelected = series.id in selectedSeriesIds
                val posterUrl = series.posterUrl ?: mediaRepository?.buildPrimaryImageUrl(series.id)
                ChecklistShowRow(
                    series = series,
                    isSelected = isSelected,
                    posterUrl = posterUrl,
                    accent = accent,
                    surface = surface,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onToggle = {
                        if (isSelected) {
                            selectedSeriesIds.remove(series.id)
                        } else {
                            selectedSeriesIds.add(series.id)
                        }
                    }
                )
            }
        }

        // Playback Configuration
        item {
            SectionHeader(title = "Playback", textPrimary = textPrimary, textSecondary = textSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            SettingRow(
                label = "Interleave Strategy",
                value = interleaveStrategy.toReadable(),
                textPrimary = textPrimary,
                accent = accent,
                surface = surface,
                onClick = { showInterleavePicker = true }
            )
        }

        item {
            SettingRow(
                label = "Audio Night Mode",
                value = audioNightMode.toReadable(),
                textPrimary = textPrimary,
                accent = accent,
                surface = surface,
                onClick = { showNightModePicker = true }
            )
        }

        item {
            SettingRow(
                label = "Skip Behavior",
                value = skipBehavior.toReadable(),
                textPrimary = textPrimary,
                accent = accent,
                surface = surface,
                onClick = { showSkipPicker = true }
            )
        }

        item {
            SettingRow(
                label = "Resume On Return",
                value = resumeBehavior.toReadable(),
                textPrimary = textPrimary,
                accent = accent,
                surface = surface,
                onClick = { showResumePicker = true }
            )
        }

        // Action buttons
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cancel
                FocusCard(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) { isFocused ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.08f))
                            .border(1.dp, if (isFocused) Color.White else Color(0x18FFFFFF), RoundedCornerShape(8.dp))
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            color = if (isFocused) Color.Black else textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Save / Create
                FocusCard(
                    onClick = doSave,
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(8.dp)
                ) { isFocused ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                when {
                                    !canSave -> surfaceVariant.copy(alpha = 0.5f)
                                    isFocused -> Color.White
                                    else -> accent
                                }
                            )
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isFocused) Color.Black else if (canSave) Color.White else textSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEditing) "Save Changes" else "Create Channel",
                                color = if (isFocused) Color.Black else if (canSave) Color.White else textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }
            }

            // Delete option (edit mode only)
            if (isEditing && onDelete != null) {
                Spacer(modifier = Modifier.height(12.dp))
                if (showDeleteConfirm) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Delete this channel?",
                            color = textSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        FocusCard(
                            onClick = { showDeleteConfirm = false },
                            shape = RoundedCornerShape(8.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .background(if (isFocused) surfaceVariant else surface)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("No", color = textPrimary, fontSize = 13.sp)
                            }
                        }
                        FocusCard(
                            onClick = { onDelete(existingChannel!!.channelId) },
                            shape = RoundedCornerShape(8.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .background(if (isFocused) Color(0xFFEF4444) else Color(0x30EF4444))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("Yes, Delete", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    FocusCard(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isFocused) Color(0x30EF4444) else surface)
                                .border(1.dp, Color(0x40EF4444), RoundedCornerShape(10.dp))
                            .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete Channel", color = Color(0xFFEF4444), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionPickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            initialFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JellycopterTheme.colors.surface,
        title = {
            Text(
                text = title,
                color = JellycopterTheme.colors.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val targetFocusValue = options.find { it.first == selectedOption }?.first ?: options.firstOrNull()?.first
                options.forEach { (value, label) ->
                    val isSelected = value == selectedOption
                    val shouldRequestFocus = value == targetFocusValue
                    val cardModifier = if (shouldRequestFocus) {
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(initialFocusRequester)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                    FocusCard(
                        onClick = { onOptionSelected(value) },
                        modifier = cardModifier,
                        shape = RoundedCornerShape(8.dp)
                    ) { isFocused ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        isFocused -> JellycopterTheme.colors.primaryAccent.copy(alpha = 0.25f)
                                        isSelected -> JellycopterTheme.colors.surfaceVariant
                                        else -> Color.Transparent
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) JellycopterTheme.colors.primaryAccent else JellycopterTheme.colors.onSurface,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = JellycopterTheme.colors.primaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FocusCard(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) { isFocused ->
                Box(
                    modifier = Modifier
                        .background(if (isFocused) JellycopterTheme.colors.primaryAccent else JellycopterTheme.colors.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Done",
                        color = if (isFocused) Color.Black else JellycopterTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}

@Composable
private fun SectionHeader(title: String, textPrimary: Color, textSecondary: Color) {
    Text(
        text = title.uppercase(),
        color = textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    textPrimary: Color,
    accent: Color,
    surface: Color,
    onClick: () -> Unit
) {
    FocusCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isFocused) accent.copy(alpha = 0.15f) else surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = textPrimary, fontSize = 14.sp)
            Text(value, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ChecklistShowRow(
    series: SeriesDetail,
    isSelected: Boolean,
    posterUrl: String?,
    accent: Color,
    surface: Color,
    textPrimary: Color,
    textSecondary: Color,
    onToggle: () -> Unit
) {
    FocusCard(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when {
                        isFocused -> accent.copy(alpha = 0.22f)
                        isSelected -> Color(0xFF0F1E16)
                        else -> surface
                    }
                )
                .border(
                    width = if (isFocused) 1.5.dp else 1.dp,
                    color = when {
                        isFocused -> accent
                        isSelected -> Color(0xFF10B981).copy(alpha = 0.4f)
                        else -> Color(0x15FFFFFF)
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Retro Checkbox Indicator
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) Color(0xFF10B981) else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) Color(0xFF10B981) else textSecondary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Poster Thumbnail
            if (!posterUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = posterUrl,
                    contentDescription = series.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 28.dp, height = 40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    error = {
                        Icon(Icons.Rounded.Tv, null, tint = accent, modifier = Modifier.size(20.dp))
                    }
                )
            } else {
                Icon(Icons.Rounded.Tv, null, tint = accent, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Show details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = series.name,
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF064E3B))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "INCLUDED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                }

                val metaDetails = buildList {
                    series.premiereYear?.let { add(it.toString()) }
                    series.communityRating?.let { add(String.format("%.1f", it)) }
                }
                if (metaDetails.isNotEmpty()) {
                    Text(
                        text = metaDetails.joinToString(" • "),
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedShowRow(
    seriesId: String,
    seriesName: String,
    posterUrl: String? = null,
    accent: Color,
    surface: Color,
    textPrimary: Color,
    textSecondary: Color,
    onRemove: () -> Unit
) {
    FocusCard(
        onClick = onRemove,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isFocused) accent.copy(alpha = 0.15f) else surface)
                .border(1.dp, if (isFocused) accent else accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (!posterUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = posterUrl,
                        contentDescription = seriesName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 28.dp, height = 40.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        error = {
                            Icon(Icons.Rounded.Tv, null, tint = accent, modifier = Modifier.size(18.dp))
                        }
                    )
                } else {
                    Icon(Icons.Rounded.Tv, null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(seriesName, color = textPrimary, fontSize = 14.sp, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Remove", color = if (isFocused) accent else textSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Rounded.Remove, contentDescription = "Remove", tint = if (isFocused) accent else textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun String.toReadable(): String = when (this) {
    "ROUND_ROBIN" -> "Round-Robin"
    "RANDOM" -> "Random"
    "WEIGHTED" -> "Weighted"
    "USE_GLOBAL" -> "Use Global"
    "ON" -> "Always On"
    "OFF" -> "Always Off"
    "RECYCLED" -> "Recycled"
    "COUNTED" -> "Counted as played"
    "NEXT_EPISODE" -> "Next Episode"
    "BEGINNING" -> "Beginning"
    "EXACT_POSITION" -> "Exact Position"
    else -> this
}
