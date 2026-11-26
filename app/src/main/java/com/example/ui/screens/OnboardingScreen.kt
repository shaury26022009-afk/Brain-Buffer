package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.AppCategoryMapping
import com.example.model.BlockedAppRule
import com.example.model.CloudSyncState
import com.example.domain.DailyFocusGoalProgress
import com.example.model.FocusGuardSettings
import com.example.model.NotificationPreferences
import com.example.model.PreferredStudyTime
import com.example.model.SessionPurposeCategory
import com.example.model.StudyLevel
import com.example.model.Subject
import com.example.model.UserPreferences
import com.example.model.UserProfileRole
import com.example.notifications.BrainBufferNotificationManager
import com.example.system.AppUsageAwarenessHelper
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassChip
import com.example.ui.components.LiquidGlassSecondaryButton
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.theme.LocalAdaptiveMetrics

private const val TOTAL_ONBOARDING_STEPS = 4

private fun isValidOnboardingEmail(raw: String): Boolean {
    val trimmed = raw.trim()
    val atIdx = trimmed.indexOf('@')
    if (atIdx <= 0) return false
    val domain = trimmed.substring(atIdx + 1)
    return domain.length >= 3 && domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.')
}

private fun defaultPurposeForRole(role: UserProfileRole): SessionPurposeCategory = when (role) {
    UserProfileRole.PROFESSIONAL -> SessionPurposeCategory.WORK
    UserProfileRole.PERSONAL -> SessionPurposeCategory.PERSONAL_TASK
    else -> SessionPurposeCategory.STUDY
}

private fun restoreSelectedPurposes(prefs: UserPreferences): Set<SessionPurposeCategory> {
    val restored = prefs.studyGoals.mapNotNull { goal ->
        SessionPurposeCategory.entries.firstOrNull {
            it.label.equals(goal, ignoreCase = true) || it.name.equals(goal, ignoreCase = true)
        }
    }.toSet()
    if (restored.isNotEmpty()) return restored
    return setOf(prefs.focusGuardSettings.defaultPurposeCategory)
}

/**
 * Concise 4-step Purpose-Aware Onboarding:
 * 0. User Type (Student, Working Professional, Personal Productivity) + optional name
 * 1. Preferred Purpose Categories + optional daily focus goal & default session duration
 * 2. Transparent Permission Explanations (Usage Access + Notifications — both optional)
 * 3. Optional Account (Continue as Guest default or Email/Password when Supabase is configured)
 *
 * All choices persist continuously to DataStore so closing the app, rotating the device,
 * or returning from Android System Usage Access Settings preserves every selection cleanly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    existingPreferences: UserPreferences,
    existingSubjects: List<Subject>,
    existingBlockedApps: List<BlockedAppRule>,
    appCategoryMappings: List<AppCategoryMapping>,
    cloudSync: CloudSyncState,
    onStepChanged: (Int) -> Unit,
    onSaveDraft: (
        step: Int,
        userRole: UserProfileRole,
        studentName: String,
        selectedPurposes: Set<SessionPurposeCategory>,
        dailyTargetMinutes: Int,
        preferredFocusMinutes: Int
    ) -> Unit = { step, _, _, _, _, _ -> onStepChanged(step) },
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String, displayName: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onResendVerification: (email: String) -> Unit = {},
    onClearAuthNotice: () -> Unit,
    onCompleteFull: (
        userRole: UserProfileRole,
        studentName: String,
        avatarKey: String,
        studyLevel: StudyLevel,
        courseOrExamName: String,
        subjects: List<Subject>,
        studyGoals: Set<String>,
        customGoal: String,
        preferredFocusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        preferredStudyTime: PreferredStudyTime,
        weeklyStudyDaysTarget: Int,
        biggestDistractions: Set<String>,
        otherDistraction: String,
        dailyTargetMinutes: Int,
        focusGuardSettings: FocusGuardSettings,
        notificationPrefs: NotificationPreferences,
        selectedBlockedApps: List<BlockedAppRule>,
        appUsageAwarenessEnabled: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val adaptive = LocalAdaptiveMetrics.current
    val scrollState = rememberScrollState()

    var currentStep by rememberSaveable {
        mutableIntStateOf(existingPreferences.onboardingCurrentStep.coerceIn(0, TOTAL_ONBOARDING_STEPS - 1))
    }

    var selectedRoleName by rememberSaveable { mutableStateOf(existingPreferences.userRole.name) }
    val selectedRole = UserProfileRole.entries.firstOrNull { it.name == selectedRoleName } ?: UserProfileRole.STUDENT

    var displayName by rememberSaveable { mutableStateOf(existingPreferences.studentName.take(36)) }
    var selectedPurposeNames by rememberSaveable {
        mutableStateOf(
            restoreSelectedPurposes(existingPreferences).map { it.name }
        )
    }
    val selectedPurposes = remember(selectedPurposeNames, selectedRole) {
        selectedPurposeNames.mapNotNull { name ->
            SessionPurposeCategory.entries.firstOrNull { it.name == name }
        }.toSet().ifEmpty { setOf(defaultPurposeForRole(selectedRole)) }
    }

    var dailyGoalMinutes by rememberSaveable {
        mutableIntStateOf(
            existingPreferences.dailyTargetMinutes.coerceIn(
                DailyFocusGoalProgress.MIN_GOAL_MINUTES,
                DailyFocusGoalProgress.MAX_GOAL_MINUTES
            )
        )
    }
    var defaultFocusMinutes by rememberSaveable {
        mutableIntStateOf(existingPreferences.preferredFocusMinutes.coerceIn(15, 90))
    }

    fun persistDraft(targetStep: Int = currentStep) {
        onSaveDraft(
            targetStep,
            selectedRole,
            displayName.trim().take(36),
            selectedPurposes,
            dailyGoalMinutes,
            defaultFocusMinutes
        )
    }

    LaunchedEffect(currentStep) {
        scrollState.scrollTo(0)
        persistDraft(currentStep)
    }

    // Live permission state refreshed on resume (never requested automatically)
    var hasUsageAccess by remember { mutableStateOf(AppUsageAwarenessHelper.hasUsageStatsPermission(context)) }
    var hasNotifications by remember { mutableStateOf(BrainBufferNotificationManager.hasNotificationPermission(context)) }
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifications = granted || BrainBufferNotificationManager.hasNotificationPermission(context)
    }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = AppUsageAwarenessHelper.hasUsageStatsPermission(context)
                hasNotifications = BrainBufferNotificationManager.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Optional email auth fields on Step 3
    var showEmailForm by rememberSaveable { mutableStateOf(false) }
    var isSignUpMode by rememberSaveable { mutableStateOf(true) }
    var email by rememberSaveable { mutableStateOf(cloudSync.session.email) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = currentStep > 0) {
        val prev = (currentStep - 1).coerceAtLeast(0)
        persistDraft(prev)
        currentStep = prev
    }

    fun finishOnboarding() {
        val primaryPurpose = selectedPurposes.firstOrNull() ?: defaultPurposeForRole(selectedRole)
        val studyLevel = when (selectedRole) {
            UserProfileRole.PROFESSIONAL -> StudyLevel.OTHER
            UserProfileRole.PERSONAL -> StudyLevel.SELF_LEARNING
            else -> existingPreferences.studyLevel
        }
        val updatedGuardSettings = existingPreferences.focusGuardSettings.copy(
            defaultPurposeCategory = primaryPurpose,
            distractionWarningsEnabled = true
        )
        onCompleteFull(
            selectedRole,
            displayName.trim().take(36),
            existingPreferences.avatarKey,
            studyLevel,
            existingPreferences.courseOrExamName,
            existingSubjects,
            selectedPurposes.map { it.label }.toSet(),
            existingPreferences.customGoal,
            defaultFocusMinutes,
            existingPreferences.shortBreakMinutes,
            existingPreferences.longBreakMinutes,
            existingPreferences.preferredStudyTime,
            existingPreferences.weeklyStudyDaysTarget,
            existingPreferences.biggestDistractions,
            existingPreferences.otherDistraction,
            dailyGoalMinutes,
            updatedGuardSettings,
            existingPreferences.notificationPrefs,
            existingBlockedApps,
            hasUsageAccess
        )
    }

    SanctuaryBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = adaptive.maxContentWidth)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .testTag("onboarding_screen"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top progress bar & back button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (currentStep > 0) {
                        IconButton(
                            onClick = {
                                val prev = (currentStep - 1).coerceAtLeast(0)
                                persistDraft(prev)
                                currentStep = prev
                            },
                            modifier = Modifier.testTag("onboarding_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Previous step",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                    Text(
                        text = "Step ${currentStep + 1} of $TOTAL_ONBOARDING_STEPS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("onboarding_step_label")
                    )
                    TextButton(
                        onClick = { finishOnboarding() },
                        modifier = Modifier.testTag("onboarding_skip_button")
                    ) {
                        Text("Skip")
                    }
                }

                LinearProgressIndicator(
                    progress = { (currentStep + 1f) / TOTAL_ONBOARDING_STEPS.toFloat() },
                    strokeCap = StrokeCap.Round,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                )

                // Step Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                        label = "onboarding_step_content"
                    ) { step ->
                        when (step) {
                            0 -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(
                                    text = "Welcome to BRAIN BUFFER",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Stay aligned with what you intend to use your phone for — without intrusive tracking or clutter.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = "How will you mainly use BRAIN BUFFER?",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                RoleSelectionCard(
                                    icon = Icons.Rounded.School,
                                    title = "Student",
                                    subtitle = "School, college, university, or exam preparation",
                                    selected = selectedRole == UserProfileRole.STUDENT,
                                    onClick = {
                                        if (selectedRole != UserProfileRole.STUDENT) {
                                            selectedRoleName = UserProfileRole.STUDENT.name
                                            selectedPurposeNames = listOf(SessionPurposeCategory.STUDY.name)
                                        }
                                    },
                                    tag = "onboarding_role_student"
                                )
                                RoleSelectionCard(
                                    icon = Icons.Rounded.Work,
                                    title = "Working Professional",
                                    subtitle = "Deep work, office tasks, meetings, and focus blocks",
                                    selected = selectedRole == UserProfileRole.PROFESSIONAL,
                                    onClick = {
                                        if (selectedRole != UserProfileRole.PROFESSIONAL) {
                                            selectedRoleName = UserProfileRole.PROFESSIONAL.name
                                            selectedPurposeNames = listOf(SessionPurposeCategory.WORK.name)
                                        }
                                    },
                                    tag = "onboarding_role_professional"
                                )
                                RoleSelectionCard(
                                    icon = Icons.Rounded.Person,
                                    title = "Personal Productivity",
                                    subtitle = "Mindful phone habits, reading, and intentional breaks",
                                    selected = selectedRole == UserProfileRole.PERSONAL,
                                    onClick = {
                                        if (selectedRole != UserProfileRole.PERSONAL) {
                                            selectedRoleName = UserProfileRole.PERSONAL.name
                                            selectedPurposeNames = listOf(SessionPurposeCategory.PERSONAL_TASK.name)
                                        }
                                    },
                                    tag = "onboarding_role_personal"
                                )

                                OutlinedTextField(
                                    value = displayName,
                                    onValueChange = { displayName = it.take(36) },
                                    label = { Text("First name (optional)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("onboarding_name_input")
                                )
                            }

                            1 -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = "Your focus purposes & goal",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Pick the purposes you use most often. You can choose or customize a purpose before every session.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                LiquidGlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 20.dp,
                                    contentPadding = PaddingValues(16.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = "Preferred purposes",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            SessionPurposeCategory.entries.forEach { cat ->
                                                val isChosen = cat in selectedPurposes
                                                LiquidGlassChip(
                                                    label = cat.label,
                                                    selected = isChosen,
                                                    onClick = {
                                                        val updated = if (isChosen && selectedPurposes.size > 1) {
                                                            selectedPurposes - cat
                                                        } else {
                                                            selectedPurposes + cat
                                                        }
                                                        selectedPurposeNames = updated.map { it.name }
                                                    },
                                                    modifier = Modifier.testTag("onboarding_purpose_${cat.name.lowercase()}")
                                                )
                                            }
                                        }
                                    }
                                }

                                LiquidGlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 20.dp,
                                    contentPadding = PaddingValues(16.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = "Optional daily focus goal",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(30, 60, 90, 120).forEach { goal ->
                                                LiquidGlassChip(
                                                    label = "${goal} min/day",
                                                    selected = dailyGoalMinutes == goal,
                                                    onClick = { dailyGoalMinutes = goal }
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Default session duration",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(15, 25, 45, 60).forEach { dur ->
                                                LiquidGlassChip(
                                                    label = "${dur}m",
                                                    selected = defaultFocusMinutes == dur,
                                                    onClick = { defaultFocusMinutes = dur }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            2 -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(
                                    text = "Transparent permissions",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Both permissions are optional. Grant them now or later in Settings.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                PermissionExplainCard(
                                    icon = Icons.Rounded.Shield,
                                    title = "Usage Access",
                                    explanation = "Used strictly at the app-package level to detect when you switch to an app outside your allowed list during a focus session. BRAIN BUFFER never reads messages, videos, websites, or screen content.",
                                    granted = hasUsageAccess,
                                    buttonLabel = "Open Usage Access",
                                    onRequest = {
                                        persistDraft(currentStep)
                                        AppUsageAwarenessHelper.openUsageAccessSettings(context)
                                    }
                                )

                                PermissionExplainCard(
                                    icon = Icons.Rounded.NotificationsActive,
                                    title = "Notifications",
                                    explanation = "Shows your live focus countdown and delivers calm reminders when a distraction is detected or temporary app access ends.",
                                    granted = hasNotifications,
                                    buttonLabel = "Allow Notifications",
                                    onRequest = {
                                        persistDraft(currentStep)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            BrainBufferNotificationManager.openAppNotificationSettings(context)
                                        }
                                    }
                                )
                            }

                            else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(
                                    text = "Optional cloud backup",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "BRAIN BUFFER works completely offline as a Guest. Sign in only if you want optional account backup.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (cloudSync.session.isSignedIn) {
                                    LiquidGlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        cornerRadius = 20.dp,
                                        contentPadding = PaddingValues(16.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                            Column {
                                                Text(
                                                    text = "Signed in",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = cloudSync.session.email.ifBlank { "Account connected" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                } else if (cloudSync.isConfigured) {
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
                                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = cloudSync.authNotice,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { showEmailForm = !showEmailForm },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("onboarding_toggle_email_form")
                                    ) {
                                        Text(if (showEmailForm) "Hide email form" else "Sign in or create account with Email")
                                    }

                                    if (showEmailForm) {
                                        val emailTrimmed = email.trim()
                                        val emailInvalid = emailTrimmed.isNotEmpty() && !isValidOnboardingEmail(emailTrimmed)
                                        val passwordTooShort = isSignUpMode && password.isNotEmpty() && password.length < 8
                                        val passwordMismatch = isSignUpMode && confirmPassword.isNotEmpty() && password != confirmPassword
                                        val canSubmitAuth = !cloudSync.isAuthBusy &&
                                            isValidOnboardingEmail(emailTrimmed) &&
                                            password.isNotBlank() &&
                                            (!isSignUpMode || (password.length >= 8 && confirmPassword == password))
                                        val showResend = isValidOnboardingEmail(emailTrimmed) && (
                                            cloudSync.lastError.orEmpty().contains("confirm", ignoreCase = true) ||
                                                cloudSync.authNotice.orEmpty().contains("verify", ignoreCase = true) ||
                                                cloudSync.authNotice.orEmpty().contains("confirm", ignoreCase = true)
                                            )

                                        LiquidGlassCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            cornerRadius = 20.dp,
                                            contentPadding = PaddingValues(16.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                OutlinedTextField(
                                                    value = email,
                                                    onValueChange = { email = it },
                                                    label = { Text("Email") },
                                                    isError = emailInvalid,
                                                    supportingText = if (emailInvalid) {
                                                        { Text("Enter a valid email address") }
                                                    } else null,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("onboarding_email_input")
                                                )
                                                OutlinedTextField(
                                                    value = password,
                                                    onValueChange = { password = it },
                                                    label = { Text("Password") },
                                                    isError = passwordTooShort,
                                                    supportingText = if (isSignUpMode) {
                                                        { Text("Minimum 8 characters") }
                                                    } else null,
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
                                                        .testTag("onboarding_password_input")
                                                )
                                                AnimatedVisibility(visible = isSignUpMode) {
                                                    OutlinedTextField(
                                                        value = confirmPassword,
                                                        onValueChange = { confirmPassword = it },
                                                        label = { Text("Confirm Password") },
                                                        isError = passwordMismatch,
                                                        supportingText = if (passwordMismatch) {
                                                            { Text("Passwords do not match") }
                                                        } else null,
                                                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(14.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .testTag("onboarding_confirm_password_input")
                                                    )
                                                }
                                                LiquidGlassButton(
                                                    onClick = {
                                                        onClearAuthNotice()
                                                        if (isSignUpMode) {
                                                            onSignUp(emailTrimmed, password, displayName.trim())
                                                        } else {
                                                            onSignIn(emailTrimmed, password)
                                                        }
                                                    },
                                                    enabled = canSubmitAuth,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("onboarding_auth_submit_button")
                                                ) {
                                                    if (cloudSync.isAuthBusy) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(18.dp),
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                    }
                                                    Text(if (isSignUpMode) "Create Account" else "Sign In")
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    TextButton(
                                                        onClick = {
                                                            isSignUpMode = !isSignUpMode
                                                            onClearAuthNotice()
                                                        },
                                                        enabled = !cloudSync.isAuthBusy
                                                    ) {
                                                        Text(if (isSignUpMode) "Already have an account?" else "Create account")
                                                    }
                                                    if (!isSignUpMode) {
                                                        TextButton(
                                                            onClick = { onResetPassword(emailTrimmed) },
                                                            enabled = !cloudSync.isAuthBusy && isValidOnboardingEmail(emailTrimmed)
                                                        ) {
                                                            Text("Forgot password?")
                                                        }
                                                    } else if (showResend) {
                                                        TextButton(
                                                            onClick = { onResendVerification(emailTrimmed) },
                                                            enabled = !cloudSync.isAuthBusy
                                                        ) {
                                                            Text("Resend email")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    LiquidGlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        cornerRadius = 20.dp,
                                        contentPadding = PaddingValues(16.dp)
                                    ) {
                                        Text(
                                            text = "Running in private offline Guest mode. All sessions and insights stay safely on this device.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Navigation Action
                LiquidGlassButton(
                    onClick = {
                        if (currentStep < TOTAL_ONBOARDING_STEPS - 1) {
                            val next = currentStep + 1
                            persistDraft(next)
                            currentStep = next
                        } else {
                            finishOnboarding()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_next_button")
                ) {
                    Text(
                        text = when {
                            currentStep < TOTAL_ONBOARDING_STEPS - 1 -> "Continue"
                            cloudSync.session.isSignedIn -> "Start Using BRAIN BUFFER"
                            else -> "Continue as Guest"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun RoleSelectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PermissionExplainCard(
    icon: ImageVector,
    title: String,
    explanation: String,
    granted: Boolean,
    buttonLabel: String,
    onRequest: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (granted) "Granted" else "Optional",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!granted) {
                LiquidGlassSecondaryButton(
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(buttonLabel)
                }
            }
        }
    }
}
