package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AppCategoryMapping
import com.example.model.BlockedAppCategory
import com.example.model.BlockedAppRule
import com.example.model.CloudSyncState
import com.example.model.ControlledAccessOption
import com.example.model.FocusGuardLevel
import com.example.model.FocusGuardSettings
import com.example.model.NotificationPreferences
import com.example.model.OnboardingOptions
import com.example.model.PreferredStudyTime
import com.example.model.StudentAvatar
import com.example.model.StudyLevel
import com.example.model.Subject
import com.example.model.SubjectIcon
import com.example.model.UserProfileRole
import com.example.service.FocusScheduleWorker
import com.example.system.AppLauncherHelper
import com.example.system.PhoneUsageAnalyticsEngine
import com.example.ui.components.BrainBufferLogoMark
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassIconContainer
import com.example.ui.components.LiquidGlassSecondaryButton
import com.example.ui.components.LiquidGlassTone
import com.example.ui.components.LivePermissionSnapshot
import com.example.ui.components.PermissionCenterSection
import com.example.ui.components.SanctuaryCard
import com.example.ui.components.StudentAvatarBadge
import com.example.ui.theme.BrandSuccess
import com.example.ui.theme.BrandWarmAmber
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.JetBrainsMonoFamily

// ============================================================================
// STEP A — WELCOME
// ============================================================================

@Composable
internal fun OnboardingWelcomeStep() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_step_welcome"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))
        BrainBufferLogoMark(size = 86.dp, animatePulse = true)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "BRAIN BUFFER",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Make space for what matters.",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "One calm workspace to protect your focus, understand phone usage, organize your work, and build healthier digital habits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WelcomePillarCard(
                icon = Icons.Rounded.Shield,
                title = "Focus without distractions",
                subtitle = "Focus Guard shields distracting apps while keeping emergency calls & timed Study access clear.",
                accent = MaterialTheme.colorScheme.primary
            )
            WelcomePillarCard(
                icon = Icons.Outlined.QueryStats,
                title = "Understand phone usage",
                subtitle = "Real on-device Usage Access analytics showing daily trends, categories, and top distractions.",
                accent = MaterialTheme.colorScheme.secondary
            )
            WelcomePillarCard(
                icon = Icons.Rounded.CalendarMonth,
                title = "Organize studies & work",
                subtitle = "Plan subjects, deadlines, and launch timed focus blocks straight from any task.",
                accent = Color(0xFF3B82F6)
            )
            WelcomePillarCard(
                icon = Icons.Rounded.Spa,
                title = "Build healthier digital habits",
                subtitle = "Respectful routines, quiet hours, and optional cloud sync — zero ads, zero covert tracking.",
                accent = Color(0xFF10B981)
            )
        }
    }
}

@Composable
private fun WelcomePillarCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color
) {
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tone = LiquidGlassTone.SUBTLE,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidGlassIconContainer(size = 42.dp, accentColor = accent, shape = CircleShape) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================================
// STEP B — ACCOUNT SETUP (Create Account / Sign In / Continue as Guest)
// ============================================================================

private enum class OnboardingAccountMode(val label: String) {
    CREATE("Create Account"),
    SIGN_IN("Sign In")
}

@Composable
internal fun OnboardingAccountStep(
    cloudSync: CloudSyncState,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String, displayName: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onResendVerification: (email: String) -> Unit = {},
    onClearNotice: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    var mode by remember { mutableStateOf(OnboardingAccountMode.CREATE) }
    var email by remember { mutableStateOf(cloudSync.session.email) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val passwordMismatch = mode == OnboardingAccountMode.CREATE &&
        confirmPassword.isNotEmpty() &&
        password != confirmPassword

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_step_account"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OnboardingStepTitle(
            eyebrow = "ACCOUNT SETUP",
            title = "Sync across devices or start locally",
            subtitle = "Create a Supabase cloud account for backup and multi-device sync, sign in to restore your setup, or continue as a Guest anytime."
        )

        if (cloudSync.session.isSignedIn) {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_account_signed_in_card"),
                tone = LiquidGlassTone.ACCENT,
                borderAccent = BrandSuccess,
                cornerRadius = 20.dp,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidGlassIconContainer(size = 44.dp, accentColor = BrandSuccess, shape = CircleShape) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = "Signed in",
                            tint = BrandSuccess,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connected to Brain Buffer Cloud",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = cloudSync.session.email.ifBlank { "Active account restored" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val providerSummary = cloudSync.session.providerLabels.joinToString(" · ")
                        Text(
                            text = if (providerSummary.isNotBlank()) {
                                "Signed in via $providerSummary · Cloud sync active"
                            } else {
                                "Your existing cloud preferences and data sync automatically."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandSuccess
                        )
                    }
                }
            }
            return
        }

        if (!cloudSync.isConfigured) {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tintOverlay = BrandWarmAmber.copy(alpha = 0.14f),
                cornerRadius = 18.dp,
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = BrandWarmAmber)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Cloud backend keys not configured yet",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Brain Buffer never fakes account creation. Continue as Guest now — all local data stays intact if you connect a Supabase account later in Profile → Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                OnboardingAccountMode.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = mode == item,
                        onClick = {
                            mode = item
                            onClearNotice()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, OnboardingAccountMode.entries.size),
                        modifier = Modifier.testTag("onboarding_auth_tab_${item.name.lowercase()}")
                    ) {
                        Text(item.label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(visible = mode == OnboardingAccountMode.CREATE) {
                Column {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = onDisplayNameChange,
                        label = { Text("Display Name") },
                        placeholder = { Text("How should we greet you?") },
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_auth_name_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
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
                    .testTag("onboarding_auth_email_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                supportingText = if (mode == OnboardingAccountMode.CREATE) {
                    { Text("At least 8 characters") }
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
                    .testTag("onboarding_auth_password_input")
            )

            AnimatedVisibility(visible = mode == OnboardingAccountMode.CREATE) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
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
                            .testTag("onboarding_auth_confirm_password_input")
                    )
                }
            }

            if (!cloudSync.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
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
                Spacer(modifier = Modifier.height(10.dp))
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

            Spacer(modifier = Modifier.height(14.dp))

            val canSubmit = cloudSync.isConfigured &&
                !cloudSync.isAuthBusy &&
                email.isNotBlank() &&
                password.isNotBlank() &&
                (mode == OnboardingAccountMode.SIGN_IN || (password.length >= 8 && confirmPassword == password))

            LiquidGlassButton(
                onClick = {
                    if (mode == OnboardingAccountMode.SIGN_IN) {
                        onSignIn(email.trim(), password)
                    } else {
                        onSignUp(email.trim(), password, displayName.trim())
                    }
                },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_auth_submit_button")
            ) {
                if (cloudSync.isAuthBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = if (mode == OnboardingAccountMode.SIGN_IN) "Sign In & Sync" else "Create Cloud Account",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            val showResend = email.contains("@") && (
                cloudSync.lastError.orEmpty().contains("confirm", ignoreCase = true) ||
                    cloudSync.authNotice.orEmpty().contains("verify your email", ignoreCase = true)
                )
            if (mode == OnboardingAccountMode.SIGN_IN || showResend) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showResend) {
                        TextButton(
                            onClick = { onResendVerification(email.trim()) },
                            enabled = cloudSync.isConfigured && !cloudSync.isAuthBusy
                        ) {
                            Text("Resend verification")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    if (mode == OnboardingAccountMode.SIGN_IN) {
                        TextButton(
                            onClick = { onResetPassword(email.trim()) },
                            enabled = cloudSync.isConfigured && !cloudSync.isAuthBusy
                        ) {
                            Text("Forgot password?")
                        }
                    }
                }
            }
        }

        LiquidGlassSecondaryButton(
            onClick = onContinueAsGuest,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_continue_guest_button")
        ) {
            Text("Continue as Guest (Local-Only)", style = MaterialTheme.typography.labelLarge)
        }

        Text(
            text = "Guest mode keeps everything locally on your device. You can create or connect a cloud account later without losing any local data.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ============================================================================
// STEP C — PERSONAL PROFILE
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnboardingProfileStep(
    role: UserProfileRole,
    onRoleChange: (UserProfileRole) -> Unit,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    avatarKey: String,
    onAvatarChange: (String) -> Unit,
    studyLevel: StudyLevel,
    onStudyLevelChange: (StudyLevel) -> Unit,
    courseOrProfession: String,
    onCourseOrProfessionChange: (String) -> Unit,
    subjects: List<Subject>,
    onToggleSuggestedSubject: (Triple<String, Long, String>) -> Unit,
    onAddCustomSubject: (String) -> Unit,
    onRemoveSubject: (String) -> Unit,
    selectedGoals: Set<String>,
    onToggleGoal: (String) -> Unit
) {
    var customSubjectDraft by remember { mutableStateOf("") }
    val areasPalette = remember(role) {
        when (role) {
            UserProfileRole.STUDENT -> OnboardingOptions.suggestedSubjects
            UserProfileRole.PROFESSIONAL -> listOf(
                Triple("Deep Work", 0xFF7B5CFA, SubjectIcon.LIGHTBULB.key),
                Triple("Engineering", 0xFF10B981, SubjectIcon.CODE.key),
                Triple("Design", 0xFFEC4899, SubjectIcon.DESIGN.key),
                Triple("Writing", 0xFFF59E0B, SubjectIcon.LANGUAGE.key),
                Triple("Strategy", 0xFF3B82F6, SubjectIcon.BOOK.key),
                Triple("Research", 0xFF06B6D4, SubjectIcon.SCIENCE.key)
            )
            UserProfileRole.PERSONAL -> listOf(
                Triple("Reading", 0xFF2FA687, SubjectIcon.BOOK.key),
                Triple("Skill Building", 0xFF7B5CFA, SubjectIcon.LIGHTBULB.key),
                Triple("Creative Work", 0xFFEC4899, SubjectIcon.DESIGN.key),
                Triple("Mindful Time", 0xFF06B6D4, SubjectIcon.HISTORY.key),
                Triple("Side Project", 0xFF10B981, SubjectIcon.CODE.key),
                Triple("Fitness & Rest", 0xFFF59E0B, SubjectIcon.SCIENCE.key)
            )
        }
    }

    val goalChoices = remember(role) {
        when (role) {
            UserProfileRole.STUDENT -> OnboardingOptions.goalOptions
            UserProfileRole.PROFESSIONAL -> listOf(
                "Finish deep work blocks",
                "Reduce phone checks",
                "Ship key projects",
                "Protect evening rest",
                "Improve daily consistency"
            )
            UserProfileRole.PERSONAL -> listOf(
                "Cut mindless scrolling",
                "Sleep better at night",
                "Read & learn daily",
                "Stay present with family",
                "Build calm screen habits"
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_step_profile"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OnboardingStepTitle(
            eyebrow = "PERSONAL PROFILE",
            title = "Tailor Brain Buffer to you",
            subtitle = "All fields below are editable anytime, and optional details can be skipped."
        )

        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "How will you mainly use Brain Buffer?",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UserProfileRole.entries.forEach { option ->
                    val selected = role == option
                    LiquidGlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("onboarding_role_${option.name.lowercase()}"),
                        tone = if (selected) LiquidGlassTone.ACCENT else LiquidGlassTone.SUBTLE,
                        borderAccent = if (selected) MaterialTheme.colorScheme.primary else null,
                        cornerRadius = 16.dp,
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp),
                        onClick = { onRoleChange(option) }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { onDisplayNameChange(it.take(36)) },
                label = { Text("Display Name (Optional)") },
                placeholder = { Text("e.g., Alex") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_name_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Avatar",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StudentAvatar.entries.forEach { avatar ->
                    val selected = avatarKey == avatar.key
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(
                                width = if (selected) 2.5.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onAvatarChange(avatar.key) }
                            .padding(3.dp)
                    ) {
                        StudentAvatarBadge(
                            avatar = avatar,
                            size = 44.dp
                        )
                    }
                }
            }
        }

        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (role == UserProfileRole.STUDENT) "Education Level & Course (Optional)" else "Focus Category & Role (Optional)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudyLevel.entries.forEach { level ->
                    val selected = studyLevel == level
                    FilterChip(
                        selected = selected,
                        onClick = { onStudyLevelChange(level) },
                        label = { Text(level.label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = courseOrProfession,
                onValueChange = { onCourseOrProfessionChange(it.take(56)) },
                label = {
                    Text(
                        when (role) {
                            UserProfileRole.STUDENT -> "Course, Major or Target Exam (Optional)"
                            UserProfileRole.PROFESSIONAL -> "Profession or Team (Optional)"
                            UserProfileRole.PERSONAL -> "Personal Focus Theme (Optional)"
                        }
                    )
                },
                placeholder = { Text(studyLevel.placeholder) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (role == UserProfileRole.STUDENT) "Your Subjects" else "Your Focus Areas",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                areasPalette.forEach { suggestion ->
                    val isAdded = subjects.any { it.name.equals(suggestion.first, ignoreCase = true) }
                    FilterChip(
                        selected = isAdded,
                        onClick = { onToggleSuggestedSubject(suggestion) },
                        label = { Text(suggestion.first) },
                        leadingIcon = if (isAdded) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customSubjectDraft,
                    onValueChange = { customSubjectDraft = it.take(36) },
                    placeholder = { Text("Add custom subject or area…") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val trimmed = customSubjectDraft.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddCustomSubject(trimmed)
                            customSubjectDraft = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add subject",
                        tint = Color.White
                    )
                }
            }
            if (subjects.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    subjects.forEach { sub ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(sub.colorHex).copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, Color(sub.colorHex).copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = sub.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove ${sub.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onRemoveSubject(sub.name) }
                                )
                            }
                        }
                    }
                }
            }
        }

        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Main Productivity Goals",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                goalChoices.forEach { goal ->
                    val selected = goal in selectedGoals
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleGoal(goal) },
                        label = { Text(goal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    }
}

// ============================================================================
// STEP D — GOALS & ROUTINE
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnboardingGoalsRoutineStep(
    dailyTargetMinutes: Int,
    onDailyTargetChange: (Int) -> Unit,
    focusMinutes: Int,
    onFocusMinutesChange: (Int) -> Unit,
    shortBreakMinutes: Int,
    onShortBreakChange: (Int) -> Unit,
    longBreakMinutes: Int,
    onLongBreakChange: (Int) -> Unit,
    weeklyDays: Int,
    onWeeklyDaysChange: (Int) -> Unit,
    preferredTime: PreferredStudyTime,
    onPreferredTimeChange: (PreferredStudyTime) -> Unit,
    quietHoursEnabled: Boolean,
    quietStartHour: Int,
    quietEndHour: Int,
    onQuietHoursChange: (enabled: Boolean, start: Int, end: Int) -> Unit,
    studyRemindersEnabled: Boolean,
    breakRemindersEnabled: Boolean,
    onRemindersChange: (study: Boolean, breaks: Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_step_goals"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OnboardingStepTitle(
            eyebrow = "GOALS & ROUTINE",
            title = "Set a sustainable rhythm",
            subtitle = "Choose your daily focus target, block lengths, working hours, and sleep/quiet hours."
        )

        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Daily Focus Target",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(30 to "30m", 60 to "1 hr", 120 to "2 hrs", 180 to "3 hrs").forEach { (mins, label) ->
                    val selected = dailyTargetMinutes == mins
                    LiquidGlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                        tone = if (selected) LiquidGlassTone.ACCENT else LiquidGlassTone.SUBTLE,
                        borderAccent = if (selected) MaterialTheme.colorScheme.primary else null,
                        cornerRadius = 14.dp,
                        contentPadding = PaddingValues(vertical = 10.dp),
                        onClick = { onDailyTargetChange(mins) }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Preferred Focus Block",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OnboardingOptions.focusDurationChoices.forEach { mins ->
                    FilterChip(
                        selected = focusMinutes == mins,
                        onClick = { onFocusMinutesChange(mins) },
                        label = { Text("${mins}m") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Short Break",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15).forEach { mins ->
                            FilterChip(
                                selected = shortBreakMinutes == mins,
                                onClick = { onShortBreakChange(mins) },
                                label = { Text("${mins}m") }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Long Break",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 20, 30).forEach { mins ->
                    FilterChip(
                        selected = longBreakMinutes == mins,
                        onClick = { onLongBreakChange(mins) },
                        label = { Text("${mins}m") }
                    )
                }
            }
        }

        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Weekly Consistency & Working Hours",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (3..7).forEach { days ->
                    val selected = weeklyDays == days
                    LiquidGlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                        tone = if (selected) LiquidGlassTone.ACCENT else LiquidGlassTone.SUBTLE,
                        borderAccent = if (selected) MaterialTheme.colorScheme.primary else null,
                        cornerRadius = 12.dp,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        onClick = { onWeeklyDaysChange(days) }
                    ) {
                        Text(
                            text = "${days}d",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PreferredStudyTime.entries.forEach { slot ->
                    FilterChip(
                        selected = preferredTime == slot,
                        onClick = { onPreferredTimeChange(slot) },
                        label = { Text("${slot.label} · ${slot.windowHint}") }
                    )
                }
            }
        }

        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            OnboardingToggleRow(
                title = "Sleep / Quiet Hours",
                subtitle = if (quietHoursEnabled) {
                    "Pauses non-active reminders ${FocusScheduleWorker.formatHour(quietStartHour)} – ${FocusScheduleWorker.formatHour(quietEndHour)}"
                } else {
                    "Silence study reminders overnight"
                },
                checked = quietHoursEnabled,
                onCheckedChange = { onQuietHoursChange(it, quietStartHour, quietEndHour) }
            )
            if (quietHoursEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                OnboardingHourStepper(
                    label = "Quiet Start",
                    hour = quietStartHour,
                    onChange = { onQuietHoursChange(true, it, quietEndHour) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                OnboardingHourStepper(
                    label = "Quiet End",
                    hour = quietEndHour,
                    onChange = { onQuietHoursChange(true, quietStartHour, it) }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BrainBufferThemeTokens.extended.cardBorder)
            OnboardingToggleRow(
                title = "Focus & Routine Reminders",
                subtitle = "Gentle daily nudge during your preferred window",
                checked = studyRemindersEnabled,
                onCheckedChange = { onRemindersChange(it, breakRemindersEnabled) }
            )
            OnboardingToggleRow(
                title = "Break Reminders",
                subtitle = "Prompt calm pauses between focus blocks",
                checked = breakRemindersEnabled,
                onCheckedChange = { onRemindersChange(studyRemindersEnabled, it) }
            )
        }
    }
}

// ============================================================================
// STEP E — FOCUS GUARD SETUP
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnboardingFocusGuardStep(
    settings: FocusGuardSettings,
    onSettingsChange: (FocusGuardSettings) -> Unit,
    selectedCategories: Set<BlockedAppCategory>,
    onToggleCategory: (BlockedAppCategory) -> Unit,
    selectedBlockedPackages: Map<String, BlockedAppRule>,
    onToggleInstalledApp: (packageName: String, label: String, category: BlockedAppCategory) -> Unit,
    appCategoryMappings: List<AppCategoryMapping>
) {
    val context = LocalContext.current
    val installedApps = remember(context, appCategoryMappings) {
        AppLauncherHelper.queryLaunchableApps(context, appCategoryMappings, limit = 36)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_step_focus_guard"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OnboardingStepTitle(
            eyebrow = "FOCUS GUARD",
            title = "Shield your attention from distractions",
            subtitle = "Choose your default protection level, distracting app categories, specific installed apps, and optional scheduled blocking."
        )

        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Default Focus Guard Mode",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusGuardLevel.entries.forEach { level ->
                    val selected = settings.defaultLevel == level
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_fg_level_${level.name.lowercase()}"),
                        tone = if (selected) LiquidGlassTone.ACCENT else LiquidGlassTone.SUBTLE,
                        borderAccent = if (selected) MaterialTheme.colorScheme.primary else null,
                        cornerRadius = 16.dp,
                        contentPadding = PaddingValues(12.dp),
                        onClick = { onSettingsChange(settings.copy(defaultLevel = level)) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${level.label} · ${level.badge}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = level.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Distracting App Categories",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Tap categories to automatically shield matching apps during Focus Guard sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    BlockedAppCategory.SOCIAL_MEDIA,
                    BlockedAppCategory.SHORT_VIDEO,
                    BlockedAppCategory.OTT_STREAMING,
                    BlockedAppCategory.GAMES,
                    BlockedAppCategory.ENTERTAINMENT
                ).forEach { category ->
                    val selected = category in selectedCategories
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleCategory(category) },
                        label = { Text(category.label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            if (installedApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Installed Apps (${selectedBlockedPackages.size} selected)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    installedApps.take(18).forEach { app ->
                        val isBlocked = selectedBlockedPackages.containsKey(app.packageName)
                        val suggestedCat = PhoneUsageAnalyticsEngine.suggestBlockedCategory(app.packageName, app.label)
                        FilterChip(
                            selected = isBlocked,
                            onClick = { onToggleInstalledApp(app.packageName, app.label, suggestedCat) },
                            label = {
                                Text(
                                    text = app.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }

        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Blocking Rules & Safety",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            OnboardingToggleRow(
                title = "Block during active focus sessions",
                subtitle = "Trigger Focus Guard shield whenever a blocked app is opened",
                checked = settings.blockDuringActiveFocus,
                onCheckedChange = { onSettingsChange(settings.copy(blockDuringActiveFocus = it)) }
            )
            OnboardingToggleRow(
                title = "Allow temporary Need Phone access",
                subtitle = "Use Study Lectures, Calls, Messages, PDFs, or Custom timed access",
                checked = settings.allowTemporaryBreaks,
                onCheckedChange = { onSettingsChange(settings.copy(allowTemporaryBreaks = it)) }
            )
            OnboardingToggleRow(
                title = "Scheduled daily focus window",
                subtitle = if (settings.scheduledBlockingEnabled) {
                    "Weekdays ${FocusScheduleWorker.formatHour(settings.scheduleStartHour)} – ${FocusScheduleWorker.formatHour(settings.scheduleEndHour)}"
                } else {
                    "Optional recurring focus schedule"
                },
                checked = settings.scheduledBlockingEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(scheduledBlockingEnabled = it)) }
            )
            if (settings.scheduledBlockingEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                OnboardingHourStepper(
                    label = "Schedule Start",
                    hour = settings.scheduleStartHour,
                    onChange = { onSettingsChange(settings.copy(scheduleStartHour = it)) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                OnboardingHourStepper(
                    label = "Schedule End",
                    hour = settings.scheduleEndHour,
                    onChange = { onSettingsChange(settings.copy(scheduleEndHour = it)) }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BrainBufferThemeTokens.extended.cardBorder)
            Text(
                text = "Always accessible: Emergency calling, system dialer, clock, alarms & Android Settings are never blocked.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Need Phone quick activities ready: ${ControlledAccessOption.entries.joinToString(" · ") { it.shortTitle }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================================
// STEP F — NOTIFICATION SETUP
// ============================================================================

@Composable
internal fun OnboardingNotificationsStep(
    notificationPrefs: NotificationPreferences,
    onNotificationPrefsChange: (NotificationPreferences) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_step_notifications"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OnboardingStepTitle(
            eyebrow = "NOTIFICATIONS & PRIVACY",
            title = "Stay calm without missing what's urgent",
            subtitle = "Brain Buffer manages only its own notifications and optional Do Not Disturb. It never reads or uploads your private messages."
        )

        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            OnboardingToggleRow(
                title = "Quiet distracting notifications in Focus",
                subtitle = "Reduce social & gaming interruptions while Focus Guard runs",
                checked = notificationPrefs.muteDistractingAppsDuringFocus,
                onCheckedChange = {
                    onNotificationPrefsChange(notificationPrefs.copy(muteDistractingAppsDuringFocus = it))
                }
            )
            OnboardingToggleRow(
                title = "Allow important & emergency calls",
                subtitle = "Phone calls remain reachable even in High Focus",
                checked = notificationPrefs.allowImportantCallsDuringFocus,
                onCheckedChange = {
                    onNotificationPrefsChange(notificationPrefs.copy(allowImportantCallsDuringFocus = it))
                }
            )
            OnboardingToggleRow(
                title = "Allow alarms during focus",
                subtitle = "System alarms always ring reliably",
                checked = notificationPrefs.allowAlarmsDuringFocus,
                onCheckedChange = {
                    onNotificationPrefsChange(notificationPrefs.copy(allowAlarmsDuringFocus = it))
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BrainBufferThemeTokens.extended.cardBorder)
            OnboardingToggleRow(
                title = "Active focus live countdown",
                subtitle = "Ongoing timer with Pause, Resume & Need Phone controls",
                checked = notificationPrefs.activeFocusEnabled,
                onCheckedChange = {
                    onNotificationPrefsChange(notificationPrefs.copy(activeFocusEnabled = it))
                }
            )
            OnboardingToggleRow(
                title = "Hide private goals on lock screen",
                subtitle = "Keeps task titles and notes private when your phone is locked",
                checked = notificationPrefs.hidePrivateNotesOnLockScreen,
                onCheckedChange = {
                    onNotificationPrefsChange(notificationPrefs.copy(hidePrivateNotesOnLockScreen = it))
                }
            )
            OnboardingToggleRow(
                title = "Upcoming tasks & exam alerts",
                subtitle = "Timely reminders for deadlines in your Planner",
                checked = notificationPrefs.upcomingTasksEnabled,
                onCheckedChange = {
                    onNotificationPrefsChange(
                        notificationPrefs.copy(upcomingTasksEnabled = it, examCountdownsEnabled = it)
                    )
                }
            )
        }
    }
}

// ============================================================================
// STEP G — ANDROID PERMISSION CENTER
// ============================================================================

@Composable
internal fun OnboardingPermissionsStep(
    onUsageAccessGranted: () -> Unit,
    onPermissionSnapshotUpdated: (LivePermissionSnapshot) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_step_permissions"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OnboardingStepTitle(
            eyebrow = "ANDROID PERMISSIONS",
            title = "Enable what you need — skip the rest",
            subtitle = "Each permission opens Android's official settings. Status updates live the moment you return, and you can change these anytime in Profile → Settings."
        )

        PermissionCenterSection(
            onUsageAccessGranted = onUsageAccessGranted,
            onStatusChanged = onPermissionSnapshotUpdated,
            showBatteryReliabilityCard = true
        )
    }
}

// ============================================================================
// STEP H — SETUP COMPLETION SUMMARY
// ============================================================================

@Composable
internal fun OnboardingCompletionStep(
    role: UserProfileRole,
    displayName: String,
    avatarKey: String,
    dailyTargetMinutes: Int,
    focusMinutes: Int,
    focusGuardSettings: FocusGuardSettings,
    blockedAppsCount: Int,
    permissionSnapshot: LivePermissionSnapshot,
    cloudSync: CloudSyncState
) {
    val avatar = StudentAvatar.entries.firstOrNull { it.key == avatarKey } ?: StudentAvatar.SPARK
    val greetingName = displayName.trim().ifBlank { role.defaultDisplayName }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_step_completion"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LiquidGlassIconContainer(
            size = 74.dp,
            accentColor = BrandSuccess,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = "Setup ready",
                tint = BrandSuccess,
                modifier = Modifier.size(34.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "You're all set, $greetingName",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Here's your personalized Brain Buffer setup. Everything can be adjusted anytime.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StudentAvatarBadge(avatar = avatar, size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greetingName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${role.label} profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BrainBufferThemeTokens.extended.cardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            SummaryRow(
                icon = Icons.Rounded.Timer,
                label = "Daily Focus Target",
                value = "$dailyTargetMinutes min / day (${focusMinutes}m blocks)"
            )
            Spacer(modifier = Modifier.height(10.dp))
            SummaryRow(
                icon = Icons.Rounded.Shield,
                label = "Focus Guard Mode",
                value = "${focusGuardSettings.defaultLevel.label} · $blockedAppsCount apps shielded"
            )
            Spacer(modifier = Modifier.height(10.dp))
            SummaryRow(
                icon = Icons.Rounded.NotificationsActive,
                label = "Android Permissions",
                value = "${permissionSnapshot.grantedCount} of 3 enabled"
            )
            Spacer(modifier = Modifier.height(10.dp))
            SummaryRow(
                icon = Icons.Rounded.PhoneAndroid,
                label = "Account Status",
                value = if (cloudSync.session.isSignedIn) {
                    val providers = cloudSync.session.providerLabels.joinToString(", ")
                    if (providers.isNotBlank()) {
                        "${cloudSync.session.email} ($providers)"
                    } else {
                        "Cloud Sync (${cloudSync.session.email})"
                    }
                } else {
                    "Local Guest Mode (connect anytime)"
                }
            )
        }

        // Final Setup Checklist: transparent Completed vs Skipped status
        SanctuaryCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_final_checklist_card")
        ) {
            Text(
                text = "SETUP CHECKLIST",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))

            ChecklistStatusRow(
                label = "Cloud Account",
                completed = cloudSync.session.isSignedIn,
                completedDetail = "Signed in",
                skippedDetail = "Guest mode"
            )
            ChecklistStatusRow(
                label = "Personal Profile",
                completed = displayName.isNotBlank(),
                completedDetail = "Configured",
                skippedDetail = "Using default"
            )
            ChecklistStatusRow(
                label = "Focus Guard",
                completed = blockedAppsCount > 0 || focusGuardSettings.scheduledBlockingEnabled,
                completedDetail = "$blockedAppsCount shielded",
                skippedDetail = "Default rules"
            )
            ChecklistStatusRow(
                label = "Usage Access Permission",
                completed = permissionSnapshot.usageAccessGranted,
                completedDetail = "Enabled",
                skippedDetail = "Skipped (enable in Settings)"
            )
            ChecklistStatusRow(
                label = "Notification Permission",
                completed = permissionSnapshot.notificationsGranted,
                completedDetail = "Enabled",
                skippedDetail = "Skipped (enable in Settings)"
            )
            ChecklistStatusRow(
                label = "Do Not Disturb Access",
                completed = permissionSnapshot.dndPolicyGranted,
                completedDetail = "Enabled",
                skippedDetail = "Optional (enable in Settings)"
            )
        }
    }
}

@Composable
private fun ChecklistStatusRow(
    label: String,
    completed: Boolean,
    completedDetail: String,
    skippedDetail: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (completed) Icons.Rounded.CheckCircle else Icons.Rounded.Timer,
                contentDescription = null,
                tint = if (completed) BrandSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Surface(
            color = if (completed) BrandSuccess.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = if (completed) completedDetail else skippedDetail,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (completed) BrandSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

// ============================================================================
// SHARED ONBOARDING BUILDING BLOCKS
// ============================================================================

@Composable
internal fun OnboardingStepTitle(
    eyebrow: String,
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OnboardingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OnboardingHourStepper(
    label: String,
    hour: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { onChange((hour + 23) % 24) }) { Text("−") }
            Text(
                text = FocusScheduleWorker.formatHour(hour),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = { onChange((hour + 1) % 24) }) { Text("+") }
        }
    }
}

@Composable
private fun SummaryRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
