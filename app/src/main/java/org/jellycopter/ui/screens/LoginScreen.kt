package org.jellycopter.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import org.jellycopter.ui.components.JellycopterLogo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jellycopter.core.model.AuthState
import org.jellycopter.core.repository.AuthRepository
import org.jellycopter.ui.components.FocusCard
import org.jellycopter.ui.theme.JellycopterTheme

@Composable
fun LoginScreen(
    authRepository: AuthRepository? = null,
    onLoginSuccess: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var serverUrl by remember {
        mutableStateOf(authRepository?.getSession()?.serverUrl ?: "http://192.168.2.4:8098")
    }
    var username by remember {
        mutableStateOf(authRepository?.getSession()?.username ?: "root")
    }
    var password by remember { mutableStateOf("nathan") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var serverProbeResult by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val performLogin: () -> Unit = {
        if (serverUrl.isBlank()) {
            errorMessage = "Please enter a server URL"
        } else if (username.isBlank()) {
            errorMessage = "Please enter a username"
        } else if (authRepository != null) {
            isLoading = true
            errorMessage = null
            scope.launch {
                android.util.Log.d("LoginScreen", "Probing $serverUrl...")
                val probe = authRepository.probeServer(serverUrl)
                probe.onSuccess { info ->
                    serverProbeResult = "Found: ${info.serverName ?: "Jellyfin"} (${info.version ?: "Unknown"})"
                    android.util.Log.d("LoginScreen", "Server probe success: ${info.serverName} (${info.version})")
                }.onFailure { err ->
                    android.util.Log.w("LoginScreen", "Server probe warning: ${err.message}")
                }
                android.util.Log.d("LoginScreen", "Authenticating '$username' at $serverUrl...")
                val result = authRepository.authenticate(serverUrl, username, password)
                isLoading = false
                result.onSuccess {
                    android.util.Log.d("LoginScreen", "Authentication successful! Navigating to Home.")
                    onLoginSuccess()
                }.onFailure { err ->
                    android.util.Log.e("LoginScreen", "Authentication failed: ${err.message}", err)
                    errorMessage = err.message ?: "Authentication failed"
                }
            }
        } else {
            onLoginSuccess()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JellycopterTheme.colors.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(JellycopterTheme.colors.surface)
                .border(
                    androidx.compose.foundation.BorderStroke(1.dp, Color(0x25FFFFFF)),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Logo & Branding
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(JellycopterTheme.colors.primaryAccent.copy(alpha = 0.2f))
                    .border(
                        androidx.compose.foundation.BorderStroke(1.dp, JellycopterTheme.colors.primaryAccent.copy(alpha = 0.5f)),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                JellycopterLogo(
                    modifier = Modifier.size(28.dp),
                    tint = JellycopterTheme.colors.primaryAccent
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Connect to Jellyfin",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Enter your server address and account credentials",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            // Error Banner Display
            if (errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x30EF4444))
                        .border(
                            androidx.compose.foundation.BorderStroke(1.dp, Color(0x60EF4444)),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = "Error",
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFFF87171)
                    )
                    Text(
                        text = errorMessage ?: "",
                        fontSize = 12.sp,
                        color = Color(0xFFFCA5A5),
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Server Probe Success Banner
            if (serverProbeResult != null && errorMessage == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x2510B981))
                        .border(
                            androidx.compose.foundation.BorderStroke(1.dp, Color(0x5010B981)),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Server Verified",
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF34D399)
                    )
                    Text(
                        text = serverProbeResult ?: "",
                        fontSize = 12.sp,
                        color = Color(0xFF6EE7B7),
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Server URL Input
            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    errorMessage = null
                    serverProbeResult = null
                },
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.2.4:8098") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = "Server",
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF94A3B8)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JellycopterTheme.colors.primaryAccent,
                    unfocusedBorderColor = Color(0x35FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Server Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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
                            errorMessage = null
                            serverProbeResult = null
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isFocused -> JellycopterTheme.colors.primaryAccent.copy(alpha = 0.25f)
                                        isSelected -> JellycopterTheme.colors.surfaceVariant
                                        else -> Color(0x20FFFFFF)
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (isSelected || isFocused) JellycopterTheme.colors.primaryAccent else Color(0x30FFFFFF),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                color = if (isSelected || isFocused) JellycopterTheme.colors.primaryAccent else Color(0xFFCBD5E1),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Username Input
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    errorMessage = null
                },
                label = { Text("Username") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = "Username",
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF94A3B8)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JellycopterTheme.colors.primaryAccent,
                    unfocusedBorderColor = Color(0x35FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Password Input
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = { Text("Password") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Password",
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF94A3B8)
                    )
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        performLogin()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JellycopterTheme.colors.primaryAccent,
                    unfocusedBorderColor = Color(0x35FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Connect / Sign-In Button (D-Pad accessible FocusCard)
            FocusCard(
                onClick = performLogin,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { isFocused ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                isFocused -> Color.White
                                else -> JellycopterTheme.colors.primaryAccent
                            }
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = if (isFocused) Color.Black else Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Login,
                                contentDescription = "Sign In",
                                modifier = Modifier.size(20.dp),
                                tint = if (isFocused) Color.Black else Color.White
                            )
                            Text(
                                text = "Connect & Sign In",
                                fontSize = 14.sp,
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
