package org.jellycopter.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellycopter.core.repository.AuthRepository
import org.jellycopter.ui.components.FocusCard
import org.jellycopter.ui.components.JellycopterLogo
import org.jellycopter.ui.theme.JellycopterTheme
import org.jellycopter.ui.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    authRepository: AuthRepository? = null,
    onLoginSuccess: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel? = null
) {
    val vm = viewModel ?: remember(authRepository) { LoginViewModel(authRepository) }
    var serverUrl by remember(vm.serverUrl) { mutableStateOf(vm.serverUrl) }
    var username by remember(vm.username) { mutableStateOf(vm.username) }
    var password by remember(vm.password) { mutableStateOf(vm.password) }

    val isLoading = vm.isLoading
    val errorMessage = vm.errorMessage
    val serverProbeResult = vm.serverProbeResult

    val focusManager = LocalFocusManager.current
    val primaryAccent = JellycopterTheme.colors.primaryAccent
    val borderGlow = JellycopterTheme.colors.borderGlow
    val ambientGlow = JellycopterTheme.colors.ambientGlow
    val bgColor = JellycopterTheme.colors.background

    val performLogin: () -> Unit = {
        vm.serverUrl = serverUrl
        vm.username = username
        vm.password = password
        vm.performLogin(onSuccess = onLoginSuccess)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        ambientGlow.copy(alpha = 0.22f),
                        ambientGlow.copy(alpha = 0.05f),
                        bgColor
                    ),
                    radius = 950f
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF171B28),
                            Color(0xFF11141E)
                        )
                    )
                )
                .border(
                    androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Aerodynamic Emblem & Branding
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                primaryAccent.copy(alpha = 0.22f),
                                primaryAccent.copy(alpha = 0.06f)
                            )
                        )
                    )
                    .border(
                        androidx.compose.foundation.BorderStroke(1.dp, primaryAccent.copy(alpha = 0.40f)),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                JellycopterLogo(
                    modifier = Modifier.size(28.dp),
                    tint = primaryAccent,
                    coreTint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "JELLYCOPTER",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = primaryAccent
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Connect to Server",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Enter your Jellyfin server address and credentials",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            // Error Banner Display
            if (errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x20EF4444))
                        .border(
                            androidx.compose.foundation.BorderStroke(1.dp, Color(0x55EF4444)),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = "Error",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFF87171)
                    )
                    Text(
                        text = errorMessage ?: "",
                        fontSize = 12.sp,
                        color = Color(0xFFFCA5A5),
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Server Probe Success Banner
            if (serverProbeResult != null && errorMessage == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1C10B981))
                        .border(
                            androidx.compose.foundation.BorderStroke(1.dp, Color(0x4010B981)),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Server Verified",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF34D399)
                    )
                    Text(
                        text = serverProbeResult ?: "",
                        fontSize = 12.sp,
                        color = Color(0xFF6EE7B7),
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Server URL Input
            TvInputField(
                label = "Server URL",
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    vm.clearErrors()
                },
                icon = Icons.Rounded.Dns,
                placeholder = "http://192.168.2.4:8096",
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
            )

            // Server Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "http://192.168.2.4:8098" to "Test (:8098)",
                    "http://192.168.2.4:8096" to "LAN (:8096)",
                    "https://jellyfin.kn8design.com" to "Domain"
                ).forEach { (url, label) ->
                    val isSelected = serverUrl == url
                    FocusCard(
                        onClick = {
                            serverUrl = url
                            vm.clearErrors()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) { isFocused ->
                        val bg = when {
                            isFocused -> primaryAccent.copy(alpha = 0.28f)
                            isSelected -> primaryAccent.copy(alpha = 0.16f)
                            else -> Color(0x12FFFFFF)
                        }
                        val borderColor = when {
                            isFocused -> primaryAccent
                            isSelected -> primaryAccent.copy(alpha = 0.55f)
                            else -> Color(0x1CFFFFFF)
                        }
                        val textColor = when {
                            isFocused -> Color.White
                            isSelected -> primaryAccent
                            else -> Color(0xFF94A3B8)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .border(
                                    width = if (isFocused) 1.5.dp else 1.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(primaryAccent)
                                )
                            }
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Username Input
            TvInputField(
                label = "Username",
                value = username,
                onValueChange = {
                    username = it
                    vm.clearErrors()
                },
                icon = Icons.Rounded.Person,
                placeholder = "Enter username",
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Password Input
            TvInputField(
                label = "Password",
                value = password,
                onValueChange = {
                    password = it
                    vm.clearErrors()
                },
                icon = Icons.Rounded.Lock,
                placeholder = "Enter password",
                isPassword = true,
                imeAction = ImeAction.Done,
                onImeAction = performLogin
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Connect & Sign In Button
            FocusCard(
                onClick = performLogin,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) { isFocused ->
                val bg = if (isFocused) {
                    Brush.horizontalGradient(
                        listOf(
                            primaryAccent,
                            borderGlow
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            primaryAccent.copy(alpha = 0.90f),
                            primaryAccent.copy(alpha = 0.82f)
                        )
                    )
                }

                val textTint = Color(0xFF090D16)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .border(
                            width = if (isFocused) 1.5.dp else 0.dp,
                            color = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = textTint,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Connecting...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = textTint
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Login,
                                contentDescription = "Sign In",
                                modifier = Modifier.size(18.dp),
                                tint = textTint
                            )
                            Text(
                                text = "Connect & Sign In",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp,
                                color = textTint
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 10-foot TV optimized input field:
 * - Idle / focused: Clean focusable TV card that navigates with remote D-pad.
 * - Editing: When clicked / OK pressed, enters text editing and brings up IME.
 */
@Composable
private fun TvInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    placeholder: String = "",
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val primaryAccent = JellycopterTheme.colors.primaryAccent

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color(0xFF718096),
            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
        )

        if (isEditing) {
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(primaryAccent),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPassword) {
                        KeyboardType.Password
                    } else if (label.contains("URL", ignoreCase = true)) {
                        KeyboardType.Uri
                    } else {
                        KeyboardType.Text
                    },
                    imeAction = imeAction
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        isEditing = false
                        onImeAction()
                    },
                    onDone = {
                        isEditing = false
                        onImeAction()
                    }
                ),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .fillMaxWidth()
                    .height(42.dp)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                AndroidKeyEvent.KEYCODE_BACK -> {
                                    isEditing = false
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_ENTER,
                                AndroidKeyEvent.KEYCODE_DPAD_CENTER -> {
                                    isEditing = false
                                    onImeAction()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2434))
                    .border(
                        width = 1.5.dp,
                        color = primaryAccent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp),
                decorationBox = { innerTextField ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = primaryAccent
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder.isNotEmpty()) {
                                Text(
                                    text = placeholder,
                                    color = Color(0xFF475569),
                                    fontSize = 13.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            )
        } else {
            FocusCard(
                onClick = { isEditing = true },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) { isCardFocused ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isCardFocused) Color(0xFF1E2434) else Color(0xFF131722)
                        )
                        .border(
                            width = if (isCardFocused) 1.5.dp else 1.dp,
                            color = if (isCardFocused) primaryAccent else Color(0x22FFFFFF),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isCardFocused) primaryAccent else Color(0xFF64748B)
                    )
                    Text(
                        text = when {
                            value.isNotEmpty() && isPassword -> "*".repeat(value.length.coerceAtMost(16))
                            value.isNotEmpty() -> value
                            else -> placeholder
                        },
                        color = if (value.isNotEmpty()) Color.White else Color(0xFF475569),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (isCardFocused) {
                        Text(
                            text = "Press OK to edit",
                            fontSize = 10.sp,
                            color = primaryAccent.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
