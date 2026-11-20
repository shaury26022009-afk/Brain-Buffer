package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.NotificationPreferences
import com.example.model.OnboardingOptions
import com.example.model.PreferredStudyTime
import com.example.model.StudentAvatar
import com.example.model.StudyLevel
import com.example.model.Subject
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import com.example.performance.DevicePerformanceSignals
import com.example.performance.PerformanceProfile
import com.example.performance.PerformanceProfileOverride
import com.example.system.AppUsageAwarenessHelper
import com.example.system.DndFocusController
import com.example.ui.components.BrainBufferLogoMark
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassIconContainer
import com.example.ui.components.LiquidGlassSegmentedControl
import com.example.ui.components.LiquidGlassTone
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.components.SanctuaryCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudentAvatarBadge
import com.example.ui.components.SubScreenTopBar
import com.example.ui.components.SubjectEditorSheetContent
import com.example.ui.components.SubjectTag
import com.example.ui.components.springClickable
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAdaptiveMetrics
import com.example.viewmodel.BrainBufferUiState

private enum class ProfileTab(val label: String) {
    INSIGHTS("Insights"),
    PROFILE("Profile"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    uiState: BrainBufferUiState,
    onUpdateTheme: (ThemeMode) -> Unit,
    onUpdateProfile: (
        studentName: String,
        avatarKey: String,
        studyLevel: StudyLevel,
        courseOrExamName: String,
        studyGoals: Set<String>,
        customGoal: String,
        preferredStudyTime: PreferredStudyTime,
        weeklyStudyDaysTarget: Int,
        biggestDistractions: Set<String>,
        otherDistraction: String
    ) -> Unit,
    onSaveSubject: (Subject) -> Unit,
    onDeleteSubject: (Long) -> Unit,
    onUpdateTimerAndTarget: (focusMin: Int, shortBreakMin: Int, longBreakMin: Int, dailyTargetMin: Int) -> Unit,
    onUpdateAppSettings: (
        notificationsEnabled: Boolean,
        breakRemindersEnabled: Boolean,
        soundEnabled: Boolean,
        hapticsEnabled: Boolean,
        reducedMotionOverride: Boolean
    ) -> Unit,
    onOpenMindReset: () -> Unit,
    onLoadDemoData: () -> Unit,
    onClearAllData: () -> Unit,
    onResetOnboarding: () -> Unit,
    onExportStudyBackup: () -> String = { "" },
    onUpdatePerformanceOverride: (PerformanceProfileOverride) -> Unit = {},
    onUpdateNotificationPrefs: (NotificationPreferences) -> Unit = {},
    onUpdateAppUsageAwareness: (Boolean) -> Unit = {}
) {
    val adaptive = LocalAdaptiveMetrics.current
    var activeTab by remember { mutableStateOf(ProfileTab.INSIGHTS) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var editingSubject by remember { mutableStateOf<Subject?>(null) }
    var showSubjectSheet by remember { mutableStateOf(false) }

    SanctuaryBackdrop {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = adaptive.maxContentWidth)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .testTag("profile_list"),
                contentPadding = PaddingValues(
                    start = adaptive.horizontalPadding,
                    end = adaptive.horizontalPadding,
                    top = 16.dp,
                    bottom = adaptive.bottomContentClearance
                ),
                verticalArrangement = Arrangement.spacedBy(adaptive.sectionSpacing)
            ) {
                if (adaptive.useTwoColumnLayout) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1.15f)) {
                                ProfileHeroCard(
                                    uiState = uiState,
                                    onEditProfile = { showProfileEditor = true }
                                )
                            }
                            Box(modifier = Modifier.weight(0.85f)) {
                                MindResetInviteBanner(
                                    resetsCount = uiState.preferences.mindResetCount,
                                    onOpenMindReset = onOpenMindReset
                                )
                            }
                        }
                    }
                } else {
                    // 1. Profile Header Card
                    item {
                        ProfileHeroCard(
                            uiState = uiState,
                            onEditProfile = { showProfileEditor = true }
                        )
                    }

                    // 2. Mind Reset Invitation Card
                    item {
                        MindResetInviteBanner(
                            resetsCount = uiState.preferences.mindResetCount,
                            onOpenMindReset = onOpenMindReset
                        )
                    }
                }

                // 3. Segmented Tab Switcher (Insights | Profile | Settings)
                item {
                    LiquidGlassSegmentedControl(
                        items = ProfileTab.entries,
                        selectedItem = activeTab,
                        onSelect = { activeTab = it },
                        labelProvider = { it.label },
                        testTagProvider = { "profile_tab_${it.name.lowercase()}" }
                    )
                }

                // 4. Tab Content
                when (activeTab) {
                    ProfileTab.INSIGHTS -> {
                        item {
                            InsightsContent(uiState = uiState)
                        }
                    }

                    ProfileTab.PROFILE -> {
                        item {
                            ProfileDetailsSection(
                                uiState = uiState,
                                onEditProfile = { showProfileEditor = true },
                                onAddSubject = {
                                    editingSubject = null
                                    showSubjectSheet = true
                                },
                                onEditSubject = { subject ->
                                    editingSubject = subject
                                    showSubjectSheet = true
                                },
                                onDeleteSubject = onDeleteSubject
                            )
                        }
                    }

                    ProfileTab.SETTINGS -> {
                        item {
                            SettingsContentSection(
                                preferences = uiState.preferences,
                                resolvedProfile = uiState.resolvedPerformanceProfile,
                                deviceSignals = uiState.deviceSignals,
                                onUpdateTheme = onUpdateTheme,
                                onUpdateTimerAndTarget = onUpdateTimerAndTarget,
                                onUpdateAppSettings = onUpdateAppSettings,
                                onUpdatePerformanceOverride = onUpdatePerformanceOverride,
                                onUpdateNotificationPrefs = onUpdateNotificationPrefs,
                                onUpdateAppUsageAwareness = onUpdateAppUsageAwareness,
                                onLoadDemoData = onLoadDemoData,
                                onClearAllData = onClearAllData,
                                onResetOnboarding = onResetOnboarding,
                                onExportStudyBackup = onExportStudyBackup
                            )
                        }
                    }
                }
            }
        }
    }

    if (showProfileEditor) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showProfileEditor = false },
            sheetState = sheetState,
            containerColor = BrainBufferThemeTokens.glass.sheetBackground
        ) {
            ProfileEditorSheetContent(
                preferences = uiState.preferences,
                onDismiss = { showProfileEditor = false },
                onSave = { name, avatarKey, level, course, goals, customGoal, studyTime, daysTarget, distractions, otherDist ->
                    onUpdateProfile(
                        name,
                        avatarKey,
                        level,
                        course,
                        goals,
                        customGoal,
                        studyTime,
                        daysTarget,
                        distractions,
                        otherDist
                    )
                    showProfileEditor = false
                }
            )
        }
    }

    if (showSubjectSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSubjectSheet = false },
            sheetState = sheetState,
            containerColor = BrainBufferThemeTokens.glass.sheetBackground
        ) {
            SubjectEditorSheetContent(
                initialSubject = editingSubject,
                onDismiss = { showSubjectSheet = false },
                onSave = { subject ->
                    onSaveSubject(subject)
                    showSubjectSheet = false
                }
            )
        }
    }
}

@Composable
fun SettingsStandaloneScreen(
    uiState: BrainBufferUiState,
    onBack: () -> Unit,
    onUpdateTheme: (ThemeMode) -> Unit,
    onUpdateTimerAndTarget: (focusMin: Int, shortBreakMin: Int, longBreakMin: Int, dailyTargetMin: Int) -> Unit,
    onUpdateAppSettings: (
        notificationsEnabled: Boolean,
        breakRemindersEnabled: Boolean,
        soundEnabled: Boolean,
        hapticsEnabled: Boolean,
        reducedMotionOverride: Boolean
    ) -> Unit,
    onLoadDemoData: () -> Unit,
    onClearAllData: () -> Unit,
    onResetOnboarding: () -> Unit,
    onExportStudyBackup: () -> String = { "" },
    onUpdatePerformanceOverride: (PerformanceProfileOverride) -> Unit = {},
    onUpdateNotificationPrefs: (NotificationPreferences) -> Unit = {},
    onUpdateAppUsageAwareness: (Boolean) -> Unit = {}
) {
    val adaptive = LocalAdaptiveMetrics.current
    SanctuaryBackdrop {
        Column(modifier = Modifier.fillMaxSize()) {
            SubScreenTopBar(
                title = "Settings",
                subtitle = "Performance, notifications, focus defaults & privacy",
                onBack = onBack
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = adaptive.maxContentWidth)
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(
                        start = adaptive.horizontalPadding,
                        end = adaptive.horizontalPadding,
                        top = 8.dp,
                        bottom = 36.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(adaptive.sectionSpacing)
                ) {
                    item {
                        SettingsContentSection(
                            preferences = uiState.preferences,
                            resolvedProfile = uiState.resolvedPerformanceProfile,
                            deviceSignals = uiState.deviceSignals,
                            onUpdateTheme = onUpdateTheme,
                            onUpdateTimerAndTarget = onUpdateTimerAndTarget,
                            onUpdateAppSettings = onUpdateAppSettings,
                            onUpdatePerformanceOverride = onUpdatePerformanceOverride,
                            onUpdateNotificationPrefs = onUpdateNotificationPrefs,
                            onUpdateAppUsageAwareness = onUpdateAppUsageAwareness,
                            onLoadDemoData = onLoadDemoData,
                            onClearAllData = onClearAllData,
                            onResetOnboarding = onResetOnboarding,
                            onExportStudyBackup = onExportStudyBackup
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeroCard(
    uiState: BrainBufferUiState,
    onEditProfile: () -> Unit
) {
    val prefs = uiState.preferences
    val subtitle = buildString {
        append(prefs.studyLevel.label)
        if (prefs.courseOrExamName.isNotBlank()) {
            append(" · ")
            append(prefs.courseOrExamName)
        }
    }

    SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StudentAvatarBadge(
                avatar = prefs.avatar,
                size = 56.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prefs.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onEditProfile,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("edit_profile_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit profile",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = BrainBufferThemeTokens.extended.cardBorder)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ProfileMiniStat(label = "Subjects", value = "${uiState.subjects.size}")
            ProfileMiniStat(label = "Sessions", value = "${uiState.focusOnlySessions.size}")
            ProfileMiniStat(label = "Streak", value = "${uiState.currentStreakDays}d")
            ProfileMiniStat(label = "Daily Goal", value = "${prefs.dailyTargetMinutes}m")
        }
    }
}

@Composable
private fun ProfileMiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MindResetInviteBanner(
    resetsCount: Int,
    onOpenMindReset: () -> Unit
) {
    val extended = BrainBufferThemeTokens.extended
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_mind_reset_banner"),
        tone = LiquidGlassTone.ACCENT,
        cornerRadius = 22.dp,
        borderAccent = if (extended.isDark) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.72f),
        contentPadding = PaddingValues(0.dp),
        onClick = onOpenMindReset
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        if (extended.isDark) {
                            listOf(
                                extended.heroGradientStart.copy(alpha = 0.88f),
                                extended.heroGradientEnd.copy(alpha = 0.94f)
                            )
                        } else {
                            listOf(
                                extended.heroGradientStart,
                                extended.heroGradientEnd
                            )
                        }
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LiquidGlassIconContainer(
                size = 44.dp,
                accentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Rounded.Spa,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Need a Mind Reset?",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "Breathing guide, brain dump & session reflection ($resetsCount resets)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.86f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileDetailsSection(
    uiState: BrainBufferUiState,
    onEditProfile: () -> Unit,
    onAddSubject: () -> Unit,
    onEditSubject: (Subject) -> Unit,
    onDeleteSubject: (Long) -> Unit
) {
    val adaptive = LocalAdaptiveMetrics.current
    val prefs = uiState.preferences
    val extended = BrainBufferThemeTokens.extended

    val subjectsCard: @Composable () -> Unit = {
        SanctuaryCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_subjects_card")
        ) {
            SectionHeader(
                title = "Your Subjects",
                subtitle = "${uiState.subjects.size} active subject${if (uiState.subjects.size == 1) "" else "s"}",
                actionLabel = "Add",
                onActionClick = onAddSubject
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.subjects.isEmpty()) {
                Text(
                    text = "Add the subjects or exams you're studying to track time, tasks, and deadlines.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.subjects.forEach { subject ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, extended.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    SubjectTag(subject = subject)
                                    val examPart = subject.examDateFormatted()?.let { " · Exam $it" } ?: ""
                                    Text(
                                        text = "${subject.targetHoursPerWeek.toInt()}h / week target$examPart",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onEditSubject(subject) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit ${subject.name}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteSubject(subject.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete ${subject.name}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val habitsCard: @Composable () -> Unit = {
        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = "Personalisation & Habits",
                subtitle = "Editable anytime",
                actionLabel = "Edit",
                onActionClick = onEditProfile
            )
            Spacer(modifier = Modifier.height(14.dp))

            ProfileKeyValueRow("Study Window", "${prefs.preferredStudyTime.label} (${prefs.preferredStudyTime.windowHint})")
            Spacer(modifier = Modifier.height(8.dp))
            ProfileKeyValueRow("Weekly Goal", "${prefs.weeklyStudyDaysTarget} days / week")

            val allGoals = buildList {
                addAll(prefs.studyGoals)
                if (prefs.customGoal.isNotBlank()) add(prefs.customGoal)
            }
            if (allGoals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Study Goals",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allGoals.forEach { goal ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = goal,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            val allDistractions = buildList {
                addAll(prefs.biggestDistractions)
                if (prefs.otherDistraction.isNotBlank()) add(prefs.otherDistraction)
            }
            if (allDistractions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Watch-outs (supportive reminders)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allDistractions.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, extended.cardBorder)
                        ) {
                            Text(
                                text = item,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.distractionTip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (adaptive.useTwoColumnLayout) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.weight(1f)) { subjectsCard() }
            Box(modifier = Modifier.weight(1f)) { habitsCard() }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            subjectsCard()
            habitsCard()
        }
    }
}

@Composable
private fun ProfileKeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsContentSection(
    preferences: UserPreferences,
    onUpdateTheme: (ThemeMode) -> Unit,
    onUpdateTimerAndTarget: (focusMin: Int, shortBreakMin: Int, longBreakMin: Int, dailyTargetMin: Int) -> Unit,
    onUpdateAppSettings: (
        notificationsEnabled: Boolean,
        breakRemindersEnabled: Boolean,
        soundEnabled: Boolean,
        hapticsEnabled: Boolean,
        reducedMotionOverride: Boolean
    ) -> Unit,
    onLoadDemoData: () -> Unit,
    onClearAllData: () -> Unit,
    onResetOnboarding: () -> Unit,
    onExportStudyBackup: () -> String = { "" },
    resolvedProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    deviceSignals: DevicePerformanceSignals = DevicePerformanceSignals(4096, false, 4, android.os.Build.VERSION.SDK_INT, 60f, false),
    onUpdatePerformanceOverride: (PerformanceProfileOverride) -> Unit = {},
    onUpdateNotificationPrefs: (NotificationPreferences) -> Unit = {},
    onUpdateAppUsageAwareness: (Boolean) -> Unit = {}
) {
    val adaptive = LocalAdaptiveMetrics.current
    val context = LocalContext.current
    val extended = BrainBufferThemeTokens.extended
    var showDemoConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var exportFeedback by remember { mutableStateOf<String?>(null) }

    val appearanceCard: @Composable () -> Unit = {
        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = "Appearance",
                subtitle = "AMOLED Dark uses pure #000000 black surfaces"
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modes = listOf(
                    Triple(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
                    Triple(ThemeMode.DARK, "AMOLED", Icons.Outlined.DarkMode),
                    Triple(ThemeMode.SYSTEM, "System", Icons.Outlined.PhoneAndroid)
                )
                modes.forEach { (mode, label, icon) ->
                    val selected = preferences.themeMode == mode
                    LiquidGlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("theme_option_${mode.name.lowercase()}"),
                        tone = if (selected) LiquidGlassTone.ACCENT else LiquidGlassTone.SUBTLE,
                        cornerRadius = 15.dp,
                        borderAccent = if (selected) MaterialTheme.colorScheme.primary else null,
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp),
                        onClick = { onUpdateTheme(mode) }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    val focusDefaultsCard: @Composable () -> Unit = {
        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = "Focus & Target Defaults",
                subtitle = "Applied automatically to new sessions"
            )
            Spacer(modifier = Modifier.height(12.dp))

            DurationStepperRow(
                label = "Focus Duration",
                valueLabel = "${preferences.preferredFocusMinutes} min",
                onDecrement = {
                    val next = (preferences.preferredFocusMinutes - 5).coerceAtLeast(10)
                    onUpdateTimerAndTarget(next, preferences.shortBreakMinutes, preferences.longBreakMinutes, preferences.dailyTargetMinutes)
                },
                onIncrement = {
                    val next = (preferences.preferredFocusMinutes + 5).coerceAtMost(90)
                    onUpdateTimerAndTarget(next, preferences.shortBreakMinutes, preferences.longBreakMinutes, preferences.dailyTargetMinutes)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = extended.cardBorder)
            DurationStepperRow(
                label = "Short Break",
                valueLabel = "${preferences.shortBreakMinutes} min",
                onDecrement = {
                    val next = (preferences.shortBreakMinutes - 1).coerceAtLeast(3)
                    onUpdateTimerAndTarget(preferences.preferredFocusMinutes, next, preferences.longBreakMinutes, preferences.dailyTargetMinutes)
                },
                onIncrement = {
                    val next = (preferences.shortBreakMinutes + 1).coerceAtMost(20)
                    onUpdateTimerAndTarget(preferences.preferredFocusMinutes, next, preferences.longBreakMinutes, preferences.dailyTargetMinutes)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = extended.cardBorder)
            DurationStepperRow(
                label = "Long Break",
                valueLabel = "${preferences.longBreakMinutes} min",
                onDecrement = {
                    val next = (preferences.longBreakMinutes - 5).coerceAtLeast(10)
                    onUpdateTimerAndTarget(preferences.preferredFocusMinutes, preferences.shortBreakMinutes, next, preferences.dailyTargetMinutes)
                },
                onIncrement = {
                    val next = (preferences.longBreakMinutes + 5).coerceAtMost(45)
                    onUpdateTimerAndTarget(preferences.preferredFocusMinutes, preferences.shortBreakMinutes, next, preferences.dailyTargetMinutes)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = extended.cardBorder)
            DurationStepperRow(
                label = "Daily Focus Target",
                valueLabel = "${preferences.dailyTargetMinutes} min",
                onDecrement = {
                    val next = (preferences.dailyTargetMinutes - 15).coerceAtLeast(15)
                    onUpdateTimerAndTarget(preferences.preferredFocusMinutes, preferences.shortBreakMinutes, preferences.longBreakMinutes, next)
                },
                onIncrement = {
                    val next = (preferences.dailyTargetMinutes + 15).coerceAtMost(360)
                    onUpdateTimerAndTarget(preferences.preferredFocusMinutes, preferences.shortBreakMinutes, preferences.longBreakMinutes, next)
                }
            )
        }
    }

    val comfortCard: @Composable () -> Unit = {
        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Preferences & Comfort")
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSwitchRow(
                icon = Icons.Outlined.Notifications,
                title = "Study Reminders",
                subtitle = "Gentle daily study nudge preferences",
                checked = preferences.notificationsEnabled,
                onCheckedChange = {
                    onUpdateAppSettings(
                        it,
                        preferences.breakRemindersEnabled,
                        preferences.soundEnabled,
                        preferences.hapticsEnabled,
                        preferences.reducedMotionOverride
                    )
                }
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.Notifications,
                title = "Break Reminders",
                subtitle = "Encourage pauses between long focus blocks",
                checked = preferences.breakRemindersEnabled,
                onCheckedChange = {
                    onUpdateAppSettings(
                        preferences.notificationsEnabled,
                        it,
                        preferences.soundEnabled,
                        preferences.hapticsEnabled,
                        preferences.reducedMotionOverride
                    )
                }
            )
            SettingsSwitchRow(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                title = "Completion Chime",
                subtitle = "Soft audio cue when a timer finishes",
                checked = preferences.soundEnabled,
                onCheckedChange = {
                    onUpdateAppSettings(
                        preferences.notificationsEnabled,
                        preferences.breakRemindersEnabled,
                        it,
                        preferences.hapticsEnabled,
                        preferences.reducedMotionOverride
                    )
                }
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.Vibration,
                title = "Haptic Feedback",
                subtitle = "Subtle tactile confirmation on timer controls",
                checked = preferences.hapticsEnabled,
                onCheckedChange = {
                    onUpdateAppSettings(
                        preferences.notificationsEnabled,
                        preferences.breakRemindersEnabled,
                        preferences.soundEnabled,
                        it,
                        preferences.reducedMotionOverride
                    )
                }
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.Tune,
                title = "Reduce Motion",
                subtitle = "Minimize animations across screens",
                checked = preferences.reducedMotionOverride,
                onCheckedChange = {
                    onUpdateAppSettings(
                        preferences.notificationsEnabled,
                        preferences.breakRemindersEnabled,
                        preferences.soundEnabled,
                        preferences.hapticsEnabled,
                        it
                    )
                }
            )
        }
    }

    val dataCard: @Composable () -> Unit = {
        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = "Data & Personalisation",
                subtitle = "Stored 100% locally on your device"
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val backupJson = onExportStudyBackup()
                    if (backupJson.isNotBlank()) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Brain Buffer Study Backup (JSON)")
                            putExtra(Intent.EXTRA_TEXT, backupJson)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(shareIntent, "Export Study Backup"))
                            exportFeedback = "Backup ready (${backupJson.length} chars) — choose where to save or share."
                        }.onFailure {
                            exportFeedback = "No share target available on this device."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_export_backup_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Study Backup (JSON)")
            }

            if (exportFeedback != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = exportFeedback ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onResetOnboarding,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_reset_onboarding_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restart Personalisation Onboarding")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showDemoConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_load_demo_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Load Demo Preview Data")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showClearConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_clear_data_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear All Study Data")
            }
        }
    }

    val aboutCard: @Composable () -> Unit = {
        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BrainBufferLogoMark(size = 48.dp, animatePulse = false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${stringResource(R.string.app_name)} · v1.0.0",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Offline-first student focus & planning space for phones and tablets. No account required, no ads, and your study data stays privately on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    val performanceCard: @Composable () -> Unit = {
        val blurState = BrainBufferThemeTokens.blurState
        SanctuaryCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_performance_card")
        ) {
            SectionHeader(
                title = "Adaptive Performance Profile",
                subtitle = "Active: ${resolvedProfile.label} (${blurState.tier.label}) · ${deviceSignals.cpuCores} cores · ${deviceSignals.totalRamMb}MB RAM · ${deviceSignals.refreshRateHz.toInt()}Hz"
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = resolvedProfile.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_adaptive_blur_status"),
                tone = LiquidGlassTone.SUBTLE,
                cornerRadius = 12.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Adaptive Blur · ${blurState.statusSummary}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PerformanceProfileOverride.entries.forEach { option ->
                    val selected = preferences.performanceOverride == option
                    LiquidGlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("perf_option_${option.name.lowercase()}"),
                        tone = if (selected) LiquidGlassTone.ACCENT else LiquidGlassTone.SUBTLE,
                        cornerRadius = 13.dp,
                        borderAccent = if (selected) MaterialTheme.colorScheme.primary else null,
                        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                        onClick = { onUpdatePerformanceOverride(option) }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.label.replace("Automatic", "Auto"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    val notificationsCard: @Composable () -> Unit = {
        val np = preferences.notificationPrefs
        SanctuaryCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_notifications_card")
        ) {
            SectionHeader(
                title = "Smart Notification Categories",
                subtitle = "Choose exactly what can gently notify you"
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsSwitchRow(
                icon = Icons.Outlined.Notifications,
                title = "Active Focus Live Timer",
                subtitle = "Ongoing countdown, progress & Pause/End controls",
                checked = np.activeFocusEnabled,
                onCheckedChange = { onUpdateNotificationPrefs(np.copy(activeFocusEnabled = it)) }
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.Notifications,
                title = "Activity Reminders",
                subtitle = "Alert when a lecture, reading, or game timer wraps up",
                checked = np.activityRemindersEnabled,
                onCheckedChange = { onUpdateNotificationPrefs(np.copy(activityRemindersEnabled = it)) }
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.Notifications,
                title = "Completed Sessions",
                subtitle = "Celebration & Return-to-Study notification actions",
                checked = np.completedSessionsEnabled,
                onCheckedChange = { onUpdateNotificationPrefs(np.copy(completedSessionsEnabled = it)) }
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.Notifications,
                title = "Upcoming Tasks & Exams",
                subtitle = "Calm reminders for due tasks and exam countdowns",
                checked = np.upcomingTasksEnabled && np.examCountdownsEnabled,
                onCheckedChange = {
                    onUpdateNotificationPrefs(np.copy(upcomingTasksEnabled = it, examCountdownsEnabled = it))
                }
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.Tune,
                title = "Lock-Screen Privacy",
                subtitle = "Hide private study goals & notes on lock screen",
                checked = np.hidePrivateNotesOnLockScreen,
                onCheckedChange = { onUpdateNotificationPrefs(np.copy(hidePrivateNotesOnLockScreen = it)) }
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.DarkMode,
                title = "Quiet Hours",
                subtitle = if (np.quietHoursEnabled) {
                    "Silences non-active reminders ${np.quietStartHour}:00 – ${np.quietEndHour}:00"
                } else {
                    "Pause study & break reminders overnight"
                },
                checked = np.quietHoursEnabled,
                onCheckedChange = { onUpdateNotificationPrefs(np.copy(quietHoursEnabled = it)) }
            )
            if (np.quietHoursEnabled) {
                DurationStepperRow(
                    label = "Quiet Start Hour",
                    valueLabel = "${np.quietStartHour}:00",
                    onDecrement = {
                        onUpdateNotificationPrefs(np.copy(quietStartHour = (np.quietStartHour + 23) % 24))
                    },
                    onIncrement = {
                        onUpdateNotificationPrefs(np.copy(quietStartHour = (np.quietStartHour + 1) % 24))
                    }
                )
                DurationStepperRow(
                    label = "Quiet End Hour",
                    valueLabel = "${np.quietEndHour}:00",
                    onDecrement = {
                        onUpdateNotificationPrefs(np.copy(quietEndHour = (np.quietEndHour + 23) % 24))
                    },
                    onIncrement = {
                        onUpdateNotificationPrefs(np.copy(quietEndHour = (np.quietEndHour + 1) % 24))
                    }
                )
            }
        }
    }

    val permissionsPrivacyCard: @Composable () -> Unit = {
        val dndGranted = DndFocusController.isAccessGranted(context)
        val usageGranted = AppUsageAwarenessHelper.hasUsageStatsPermission(context)
        SanctuaryCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_permissions_privacy_card")
        ) {
            SectionHeader(
                title = "Focus Permissions & Privacy",
                subtitle = "100% optional, local-only & reversible anytime"
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { DndFocusController.openDndAccessSettings(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (dndGranted) "Do Not Disturb Access: Granted ✓ (Manage)"
                    else "Grant Voluntary Do Not Disturb Access"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            SettingsSwitchRow(
                icon = Icons.Outlined.PhoneAndroid,
                title = "Local App-Usage Suggestions",
                subtitle = "Optional. Suggests switching activities (e.g., YouTube → Lecture) locally on-device. Never reads content or auto-counts study time.",
                checked = preferences.appUsageAwarenessEnabled && usageGranted,
                onCheckedChange = { enabled ->
                    if (enabled && !usageGranted) {
                        AppUsageAwarenessHelper.openUsageAccessSettings(context)
                    } else {
                        onUpdateAppUsageAwareness(enabled)
                    }
                }
            )

            if (!usageGranted) {
                OutlinedButton(
                    onClick = { AppUsageAwarenessHelper.openUsageAccessSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open Android Usage Access Settings (Optional)")
                }
            }
        }
    }

    if (adaptive.useTwoColumnLayout) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_tablet_two_column"),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                appearanceCard()
                performanceCard()
                focusDefaultsCard()
                aboutCard()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                notificationsCard()
                permissionsPrivacyCard()
                comfortCard()
                dataCard()
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            appearanceCard()
            performanceCard()
            notificationsCard()
            permissionsPrivacyCard()
            focusDefaultsCard()
            comfortCard()
            dataCard()
            aboutCard()
        }
    }

    if (showDemoConfirm) {
        AlertDialog(
            onDismissRequest = { showDemoConfirm = false },
            title = { Text("Load Demo Preview Data?") },
            text = {
                Text("This will add sample subjects, tasks, focus sessions, and garden plants so you can preview charts and visuals. You can clear everything anytime.")
            },
            confirmButton = {
                Button(onClick = {
                    showDemoConfirm = false
                    onLoadDemoData()
                }) {
                    Text("Load Preview")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDemoConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Study Data?") },
            text = {
                Text("This removes all saved tasks, focus sessions, garden plants, and reflection notes from this device. Your profile settings stay intact.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DurationStepperRow(
    label: String,
    valueLabel: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onDecrement,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "Decrease $label"
                )
            }
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = onIncrement,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Increase $label"
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditorSheetContent(
    preferences: UserPreferences,
    onDismiss: () -> Unit,
    onSave: (
        studentName: String,
        avatarKey: String,
        studyLevel: StudyLevel,
        courseOrExamName: String,
        studyGoals: Set<String>,
        customGoal: String,
        preferredStudyTime: PreferredStudyTime,
        weeklyStudyDaysTarget: Int,
        biggestDistractions: Set<String>,
        otherDistraction: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(preferences.studentName) }
    var selectedAvatar by remember { mutableStateOf(preferences.avatar) }
    var studyLevel by remember { mutableStateOf(preferences.studyLevel) }
    var courseName by remember { mutableStateOf(preferences.courseOrExamName) }
    var selectedGoals by remember { mutableStateOf(preferences.studyGoals) }
    var customGoal by remember { mutableStateOf(preferences.customGoal) }
    var studyTime by remember { mutableStateOf(preferences.preferredStudyTime) }
    var weeklyDays by remember { mutableIntStateOf(preferences.weeklyStudyDaysTarget) }
    var selectedDistractions by remember { mutableStateOf(preferences.biggestDistractions) }
    var otherDistraction by remember { mutableStateOf(preferences.otherDistraction) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Edit Your Study Profile",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your Name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_editor_name_input")
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Avatar",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudentAvatar.entries.forEach { avatar ->
                    val selected = avatar == selectedAvatar
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .springClickable { selectedAvatar = avatar }
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.5.dp,
                            if (selected) MaterialTheme.colorScheme.primary else BrainBufferThemeTokens.extended.cardBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StudentAvatarBadge(avatar = avatar, size = 28.dp)
                            Text(
                                text = avatar.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "What are you studying?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudyLevel.entries.forEach { level ->
                    val selected = level == studyLevel
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .springClickable { studyLevel = level }
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else BrainBufferThemeTokens.extended.cardBorder
                        )
                    ) {
                        Text(
                            text = level.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = courseName,
            onValueChange = { courseName = it },
            label = { Text("Course, Class, Degree or Exam") },
            placeholder = { Text(studyLevel.placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Preferred Study Time",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreferredStudyTime.entries.forEach { option ->
                    val selected = option == studyTime
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .springClickable { studyTime = option }
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else BrainBufferThemeTokens.extended.cardBorder
                        )
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        DurationStepperRow(
            label = "Study Days / Week",
            valueLabel = "$weeklyDays days",
            onDecrement = { weeklyDays = (weeklyDays - 1).coerceAtLeast(1) },
            onIncrement = { weeklyDays = (weeklyDays + 1).coerceAtMost(7) }
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Study Goals",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OnboardingOptions.goalOptions.forEach { goal ->
                    val selected = goal in selectedGoals
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .springClickable {
                                selectedGoals = if (selected) selectedGoals - goal else selectedGoals + goal
                            }
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else BrainBufferThemeTokens.extended.cardBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = goal,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = customGoal,
                onValueChange = { customGoal = it },
                label = { Text("Custom Goal (Optional)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Biggest Distractions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OnboardingOptions.distractionOptions.forEach { distraction ->
                    val selected = distraction in selectedDistractions
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .springClickable {
                                selectedDistractions = if (selected) {
                                    selectedDistractions - distraction
                                } else {
                                    selectedDistractions + distraction
                                }
                            }
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else BrainBufferThemeTokens.extended.cardBorder
                        )
                    ) {
                        Text(
                            text = distraction,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            OutlinedTextField(
                value = otherDistraction,
                onValueChange = { otherDistraction = it },
                label = { Text("Other Distraction (Optional)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        selectedAvatar.key,
                        studyLevel,
                        courseName.trim(),
                        selectedGoals,
                        customGoal.trim(),
                        studyTime,
                        weeklyDays,
                        selectedDistractions,
                        otherDistraction.trim()
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("profile_editor_save_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save Changes")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
