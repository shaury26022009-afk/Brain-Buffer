package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.CloudSyncState
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.BrandSuccess
import com.example.ui.theme.BrandWarmAmber

private enum class AuthScreenTab(val label: String) {
    SIGN_IN("Sign In"),
    SIGN_UP("Create Account")
}

/**
 * Liquid Glass Supabase Email Authentication Screen.
 * Supports:
 * - Email Sign In, Create Account (with Confirm Password), Resend Verification, and Password Reset
 * - Continue Offline / Guest (preserving all local data)
 */
@Composable
fun AuthScreen(
    cloudSync: CloudSyncState,
    defaultDisplayName: String,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String, displayName: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onResendVerification: (email: String) -> Unit = {},
    onClearNotice: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler(onBack = onNavigateBack)

    var selectedTab by remember { mutableStateOf(AuthScreenTab.SIGN_IN) }
    var email by remember { mutableStateOf(cloudSync.session.email) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf(defaultDisplayName) }
    var showPassword by remember { mutableStateOf(false) }

    val initialSignedIn = remember { cloudSync.session.isSignedIn }
    LaunchedEffect(cloudSync.session.isSignedIn) {
        if (!initialSignedIn && cloudSync.session.isSignedIn) {
            onNavigateBack()
        }
    }

    val passwordMismatch = selectedTab == AuthScreenTab.SIGN_UP &&
        confirmPassword.isNotEmpty() &&
        password != confirmPassword

    val showResendVerification = email.contains("@") && (
        cloudSync.lastError.orEmpty().contains("confirm", ignoreCase = true) ||
            cloudSync.authNotice.orEmpty().contains("verify your email", ignoreCase = true) ||
            cloudSync.authNotice.orEmpty().contains("confirm your account", ignoreCase = true)
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("auth_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .testTag("auth_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Column {
                Text(
                    text = "Brain Buffer Account",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Email Account · Offline-first Supabase sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!cloudSync.isConfigured) {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tintOverlay = BrandWarmAmber.copy(alpha = 0.14f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = BrandWarmAmber
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Supabase Project Keys Needed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Set SUPABASE_URL and SUPABASE_ANON_KEY in the AI Studio Secrets panel for optional cloud sync. Meanwhile, Brain Buffer works fully offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Email Authentication Card
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tintOverlay = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AuthScreenTab.entries.forEachIndexed { index, tab ->
                        SegmentedButton(
                            selected = selectedTab == tab,
                            onClick = {
                                selectedTab = tab
                                onClearNotice()
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, AuthScreenTab.entries.size),
                            modifier = Modifier.testTag("auth_tab_${tab.name.lowercase()}")
                        ) {
                            Text(tab.label)
                        }
                    }
                }

                AnimatedVisibility(visible = selectedTab == AuthScreenTab.SIGN_UP) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_name_input")
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    supportingText = if (selectedTab == AuthScreenTab.SIGN_UP) {
                        { Text("Minimum 8 characters") }
                    } else null,
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input")
                )

                AnimatedVisibility(visible = selectedTab == AuthScreenTab.SIGN_UP) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        isError = passwordMismatch,
                        supportingText = if (passwordMismatch) {
                            { Text("Passwords do not match") }
                        } else null,
                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_confirm_password_input")
                    )
                }

                if (!cloudSync.lastError.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = cloudSync.lastError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                if (!cloudSync.authNotice.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandSuccess.copy(alpha = 0.16f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = cloudSync.authNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                val canSubmit = cloudSync.isConfigured &&
                    !cloudSync.isAuthBusy &&
                    email.isNotBlank() &&
                    password.isNotBlank() &&
                    (selectedTab == AuthScreenTab.SIGN_IN || (password.length >= 8 && confirmPassword == password))

                LiquidGlassButton(
                    onClick = {
                        if (selectedTab == AuthScreenTab.SIGN_IN) {
                            onSignIn(email, password)
                        } else {
                            onSignUp(email, password, displayName)
                        }
                    },
                    enabled = canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_submit_button")
                ) {
                    if (cloudSync.isAuthBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = if (selectedTab == AuthScreenTab.SIGN_IN) "Sign In & Sync" else "Create Account",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showResendVerification) {
                        TextButton(
                            onClick = { onResendVerification(email) },
                            enabled = cloudSync.isConfigured && !cloudSync.isAuthBusy,
                            modifier = Modifier.testTag("auth_resend_verification_button")
                        ) {
                            Text("Resend verification")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    if (selectedTab == AuthScreenTab.SIGN_IN) {
                        TextButton(
                            onClick = { onResetPassword(email) },
                            enabled = cloudSync.isConfigured && !cloudSync.isAuthBusy,
                            modifier = Modifier.testTag("auth_forgot_password_button")
                        ) {
                            Text("Forgot password?")
                        }
                    }
                }
            }
        }

        LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = BrandSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Privacy-First Cloud Sync",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "• Syncs your profile, subjects, planner tasks, focus sessions, Focus Guard rules & Study YouTube links across devices.\n" +
                        "• Protected by strict Row-Level Security — only your signed-in account can read or modify your records.\n" +
                        "• Upgrading from Guest mode preserves your existing local data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("auth_continue_offline_button")
        ) {
            Icon(Icons.Outlined.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Continue Offline on This Device")
        }
    }
}
