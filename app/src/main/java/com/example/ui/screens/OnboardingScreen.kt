package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.OnboardingOptions
import com.example.model.PreferredStudyTime
import com.example.model.StudentAvatar
import com.example.model.StudyLevel
import com.example.model.Subject
import com.example.model.UserPreferences
import com.example.ui.components.BrainBufferLogoMark
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.components.SanctuaryCard
import com.example.ui.components.StudentAvatarBadge
import com.example.ui.components.SubjectEditorSheetContent
import com.example.ui.components.SubjectTag
import com.example.ui.components.springClickable
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.JetBrainsMonoFamily

private const val TOTAL_STEPS = 9

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    existingPreferences: UserPreferences,
    existingSubjects: List<Subject>,
    onComplete: (
        studentName: String,
        avatarKey: String,
        studyLevel: StudyLevel,
        courseOrExamName: String,
        subjects: List<Subject>,
        studyGoals: Set<String>,
        customGoal: String,
        preferredFocusMinutes: Int,
        shortBreakMinutes: Int,
        preferredStudyTime: PreferredStudyTime,
        weeklyStudyDaysTarget: Int,
        biggestDistractions: Set<String>,
        otherDistraction: String,
        dailyTargetMinutes: Int
    ) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) } // 0..8 (Steps 1..9)

    var studentName by remember { mutableStateOf(existingPreferences.studentName) }
    var selectedAvatar by remember { mutableStateOf(existingPreferences.avatar) }
    var studyLevel by remember { mutableStateOf(existingPreferences.studyLevel) }
    var courseOrExam by remember { mutableStateOf(existingPreferences.courseOrExamName) }

    val draftSubjects = remember {
        mutableStateListOf<Subject>().apply {
            addAll(existingSubjects)
        }
    }
    var showSubjectSheet by remember { mutableStateOf(false) }
    var editingSubjectIndex by remember { mutableStateOf<Int?>(null) }

    var selectedGoals by remember { mutableStateOf(existingPreferences.studyGoals) }
    var customGoal by remember { mutableStateOf(existingPreferences.customGoal) }

    var preferredFocusMin by remember { mutableIntStateOf(existingPreferences.preferredFocusMinutes) }
    var shortBreakMin by remember { mutableIntStateOf(existingPreferences.shortBreakMinutes) }
    var preferredStudyTime by remember { mutableStateOf(existingPreferences.preferredStudyTime) }
    var weeklyStudyDays by remember { mutableIntStateOf(existingPreferences.weeklyStudyDaysTarget) }

    var selectedDistractions by remember { mutableStateOf(existingPreferences.biggestDistractions) }
    var otherDistraction by remember { mutableStateOf(existingPreferences.otherDistraction) }

    var dailyTargetMin by remember { mutableIntStateOf(existingPreferences.dailyTargetMinutes) }

    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val progress by animateFloatAsState(
        targetValue = (currentStep + 1).toFloat() / TOTAL_STEPS.toFloat(),
        label = "onboardingProgress"
    )

    BackHandler(enabled = currentStep > 0) {
        currentStep = (currentStep - 1).coerceAtLeast(0)
    }

    fun finishOnboarding() {
        onComplete(
            studentName,
            selectedAvatar.key,
            studyLevel,
            courseOrExam,
            draftSubjects.toList(),
            selectedGoals,
            customGoal,
            preferredFocusMin,
            shortBreakMin,
            preferredStudyTime,
            weeklyStudyDays,
            selectedDistractions,
            otherDistraction,
            dailyTargetMin
        )
    }

    SanctuaryBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .padding(horizontal = 22.dp, vertical = 12.dp)
                .testTag("onboarding_screen")
        ) {
            // Top bar (Step counter, Back & Skip for optional steps 2..8)
            if (currentStep > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { currentStep = (currentStep - 1).coerceAtLeast(0) },
                        modifier = Modifier
                            .testTag("onboarding_back_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Text(
                        text = "Step ${currentStep + 1} of $TOTAL_STEPS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (currentStep in 1..7) {
                        TextButton(
                            onClick = { currentStep = (currentStep + 1).coerceAtMost(TOTAL_STEPS - 1) },
                            modifier = Modifier
                                .testTag("onboarding_skip_step_button")
                                .minimumInteractiveComponentSize()
                        ) {
                            Text(
                                text = "Skip",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(18.dp))
            }

            // Animated Step Body
            AnimatedContent(
                targetState = currentStep,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        val forward = targetState >= initialState
                        (slideInHorizontally(tween(260)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(220)))
                            .togetherWith(
                                slideOutHorizontally(tween(220)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(180))
                            )
                    }
                },
                label = "onboardingStep"
            ) { step ->
                when (step) {
                    0 -> Step1Welcome()
                    1 -> Step2NameAndAvatar(
                        studentName = studentName,
                        onNameChange = { studentName = it },
                        selectedAvatar = selectedAvatar,
                        onSelectAvatar = { selectedAvatar = it }
                    )
                    2 -> Step3WhatAreYouStudying(
                        studyLevel = studyLevel,
                        onSelectLevel = { studyLevel = it },
                        courseOrExam = courseOrExam,
                        onCourseChange = { courseOrExam = it }
                    )
                    3 -> Step4Subjects(
                        subjects = draftSubjects,
                        onQuickAddSuggested = { title, hex, icon ->
                            if (draftSubjects.none { it.name.equals(title, ignoreCase = true) }) {
                                draftSubjects.add(
                                    Subject(
                                        name = title,
                                        colorHex = hex,
                                        iconKey = icon,
                                        targetHoursPerWeek = 4f
                                    )
                                )
                            }
                        },
                        onAddCustom = {
                            editingSubjectIndex = null
                            showSubjectSheet = true
                        },
                        onEdit = { index ->
                            editingSubjectIndex = index
                            showSubjectSheet = true
                        },
                        onDelete = { index ->
                            if (index in draftSubjects.indices) draftSubjects.removeAt(index)
                        }
                    )
                    4 -> Step5Goals(
                        selectedGoals = selectedGoals,
                        onToggleGoal = { goal ->
                            selectedGoals = if (goal in selectedGoals) selectedGoals - goal else selectedGoals + goal
                        },
                        customGoal = customGoal,
                        onCustomGoalChange = { customGoal = it }
                    )
                    5 -> Step6StudyHabits(
                        preferredFocusMin = preferredFocusMin,
                        onSelectFocusMin = { preferredFocusMin = it },
                        shortBreakMin = shortBreakMin,
                        onSelectBreakMin = { shortBreakMin = it },
                        preferredStudyTime = preferredStudyTime,
                        onSelectStudyTime = { preferredStudyTime = it },
                        weeklyStudyDays = weeklyStudyDays,
                        onSelectWeeklyDays = { weeklyStudyDays = it }
                    )
                    6 -> Step7Distractions(
                        selectedDistractions = selectedDistractions,
                        onToggleDistraction = { item ->
                            selectedDistractions = if (item in selectedDistractions) {
                                selectedDistractions - item
                            } else {
                                selectedDistractions + item
                            }
                        },
                        otherDistraction = otherDistraction,
                        onOtherChange = { otherDistraction = it }
                    )
                    7 -> Step8DailyTarget(
                        dailyTargetMin = dailyTargetMin,
                        onSelectDailyTarget = { dailyTargetMin = it }
                    )
                    else -> Step9Summary(
                        studentName = studentName.trim().ifBlank { "Student" },
                        avatar = selectedAvatar,
                        studyLevel = studyLevel,
                        courseOrExam = courseOrExam,
                        subjects = draftSubjects,
                        dailyTargetMin = dailyTargetMin,
                        preferredFocusMin = preferredFocusMin,
                        weeklyStudyDays = weeklyStudyDays
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Navigation Button
            val ctaLabel = when (currentStep) {
                0 -> stringResource(R.string.action_get_started)
                TOTAL_STEPS - 1 -> "Enter Brain Buffer"
                else -> stringResource(R.string.action_continue)
            }

            Button(
                onClick = {
                    if (currentStep == TOTAL_STEPS - 1) {
                        finishOnboarding()
                    } else {
                        currentStep += 1
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag(if (currentStep == TOTAL_STEPS - 1) "onboarding_finish_button" else "onboarding_next_button")
            ) {
                Text(
                    text = ctaLabel,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (currentStep == TOTAL_STEPS - 1) {
                        Icons.Rounded.AutoAwesome
                    } else {
                        Icons.AutoMirrored.Rounded.ArrowForward
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    if (showSubjectSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val editing = editingSubjectIndex?.let { idx -> draftSubjects.getOrNull(idx) }
        ModalBottomSheet(
            onDismissRequest = { showSubjectSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            SubjectEditorSheetContent(
                initialSubject = editing,
                onDismiss = { showSubjectSheet = false },
                onSave = { updated ->
                    val idx = editingSubjectIndex
                    if (idx != null && idx in draftSubjects.indices) {
                        draftSubjects[idx] = updated
                    } else {
                        draftSubjects.add(updated)
                    }
                    showSubjectSheet = false
                }
            )
        }
    }
}

@Composable
private fun Step1Welcome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BrainBufferLogoMark(size = 120.dp, animatePulse = true)
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.app_brand_display),
            style = MaterialTheme.typography.headlineLarge.copy(
                letterSpacing = 3.0.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "A calm, distraction-free study companion built around your subjects, pace, and habits. Let's tailor your study space in under a minute—everything stays privately on your device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun Step2NameAndAvatar(
    studentName: String,
    onNameChange: (String) -> Unit,
    selectedAvatar: StudentAvatar,
    onSelectAvatar: (StudentAvatar) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        StepTitleHeader(
            eyebrow = "ABOUT YOU",
            title = "What should we call you?",
            subtitle = "We'll use your name for friendly greetings—never shared outside your device."
        )

        OutlinedTextField(
            value = studentName,
            onValueChange = { if (it.length <= 28) onNameChange(it) },
            label = { Text("Your name") },
            placeholder = { Text("e.g., Shaury") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_name_input")
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Pick an optional avatar emblem",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StudentAvatar.entries.forEach { avatar ->
                    val selected = selectedAvatar == avatar
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .springClickable(onClick = { onSelectAvatar(avatar) })
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else BrainBufferThemeTokens.extended.cardBorder
                            ),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            StudentAvatarBadge(
                                avatar = avatar,
                                size = 46.dp,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                        Text(
                            text = avatar.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step3WhatAreYouStudying(
    studyLevel: StudyLevel,
    onSelectLevel: (StudyLevel) -> Unit,
    courseOrExam: String,
    onCourseChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        StepTitleHeader(
            eyebrow = "YOUR PATH",
            title = "What are you studying?",
            subtitle = "Helps Brain Buffer match your study planner and exam reminders."
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StudyLevel.entries.forEach { level ->
                val selected = studyLevel == level
                SelectablePillChip(
                    label = level.label,
                    selected = selected,
                    onClick = { onSelectLevel(level) },
                    testTag = "onboarding_level_${level.name.lowercase()}"
                )
            }
        }

        OutlinedTextField(
            value = courseOrExam,
            onValueChange = { if (it.length <= 50) onCourseChange(it) },
            label = { Text("Course, class, degree, or exam (optional)") },
            placeholder = { Text(studyLevel.placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_course_input")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step4Subjects(
    subjects: List<Subject>,
    onQuickAddSuggested: (String, Long, String) -> Unit,
    onAddCustom: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepTitleHeader(
            eyebrow = "YOUR SUBJECTS",
            title = "Add the subjects you're focusing on",
            subtitle = "Tap quick suggestions or customize colours, target hours, and exam dates."
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OnboardingOptions.suggestedSubjects.forEach { (title, hex, icon) ->
                val alreadyAdded = subjects.any { it.name.equals(title, ignoreCase = true) }
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .springClickable(onClick = { onQuickAddSuggested(title, hex, icon) })
                        .minimumInteractiveComponentSize(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (alreadyAdded) Color(hex).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (alreadyAdded) Color(hex) else BrainBufferThemeTokens.extended.cardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (alreadyAdded) Icons.Rounded.Check else Icons.Rounded.Add,
                            contentDescription = null,
                            tint = if (alreadyAdded) Color(hex) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (alreadyAdded) Color(hex) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onAddCustom,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("onboarding_add_custom_subject_button")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add Custom Subject")
        }

        if (subjects.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Added subjects (${subjects.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                subjects.forEachIndexed { index, subject ->
                    SanctuaryCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                SubjectTag(subject = subject)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = buildString {
                                        append("${subject.targetHoursPerWeek.toInt()}h/week")
                                        subject.examDateFormatted()?.let { append(" · Exam $it") }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(onClick = { onEdit(index) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit subject",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                IconButton(onClick = { onDelete(index) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete subject",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step5Goals(
    selectedGoals: Set<String>,
    onToggleGoal: (String) -> Unit,
    customGoal: String,
    onCustomGoalChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        StepTitleHeader(
            eyebrow = "YOUR GOALS",
            title = "What matters most right now?",
            subtitle = "Choose as many as you like, or write your own personal intention."
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OnboardingOptions.goalOptions.forEach { goal ->
                SelectablePillChip(
                    label = goal,
                    selected = goal in selectedGoals,
                    onClick = { onToggleGoal(goal) }
                )
            }
        }

        OutlinedTextField(
            value = customGoal,
            onValueChange = { if (it.length <= 60) onCustomGoalChange(it) },
            label = { Text("Custom study goal (optional)") },
            placeholder = { Text("e.g., Master Data Structures before finals") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step6StudyHabits(
    preferredFocusMin: Int,
    onSelectFocusMin: (Int) -> Unit,
    shortBreakMin: Int,
    onSelectBreakMin: (Int) -> Unit,
    preferredStudyTime: PreferredStudyTime,
    onSelectStudyTime: (PreferredStudyTime) -> Unit,
    weeklyStudyDays: Int,
    onSelectWeeklyDays: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        StepTitleHeader(
            eyebrow = "STUDY RHYTHM",
            title = "How do you study best?",
            subtitle = "We'll pre-set your Focus Timer and weekly consistency rhythm."
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Preferred focus session length",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OnboardingOptions.focusDurationChoices.forEach { mins ->
                    SelectablePillChip(
                        label = "$mins min",
                        selected = preferredFocusMin == mins,
                        onClick = { onSelectFocusMin(mins) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Preferred short break",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingOptions.breakDurationChoices.forEach { mins ->
                    SelectablePillChip(
                        label = "$mins min",
                        selected = shortBreakMin == mins,
                        onClick = { onSelectBreakMin(mins) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "When do you usually study?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreferredStudyTime.entries.forEach { time ->
                    SelectablePillChip(
                        label = "${time.label} (${time.windowHint})",
                        selected = preferredStudyTime == time,
                        onClick = { onSelectStudyTime(time) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Study days per week: $weeklyStudyDays days",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (1..7).forEach { dayCount ->
                    val selected = weeklyStudyDays == dayCount
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .springClickable(onClick = { onSelectWeeklyDays(dayCount) })
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else BrainBufferThemeTokens.extended.cardBorder
                        )
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$dayCount",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step7Distractions(
    selectedDistractions: Set<String>,
    onToggleDistraction: (String) -> Unit,
    otherDistraction: String,
    onOtherChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        StepTitleHeader(
            eyebrow = "GENTLE AWARENESS",
            title = "What usually interrupts your focus?",
            subtitle = "Zero judgement—we use this to offer practical, supportive tips when you need a reset."
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OnboardingOptions.distractionOptions.forEach { item ->
                SelectablePillChip(
                    label = item,
                    selected = item in selectedDistractions,
                    onClick = { onToggleDistraction(item) }
                )
            }
        }

        if ("Other" in selectedDistractions) {
            OutlinedTextField(
                value = otherDistraction,
                onValueChange = { if (it.length <= 50) onOtherChange(it) },
                label = { Text("Anything else?") },
                placeholder = { Text("e.g., Overthinking or fatigue") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Step8DailyTarget(
    dailyTargetMin: Int,
    onSelectDailyTarget: (Int) -> Unit
) {
    val presetLabels = listOf(
        30 to "30 minutes · Light & steady",
        60 to "1 hour · Balanced daily rhythm",
        120 to "2 hours · Deep academic pace",
        180 to "3 hours · Intensive exam season"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepTitleHeader(
            eyebrow = "DAILY TARGET",
            title = "Pick a realistic daily study target",
            subtitle = "You can adjust this anytime. Reaching it unlocks calm decorations in My Study Garden."
        )

        presetLabels.forEach { (minutes, desc) ->
            val selected = dailyTargetMin == minutes
            SanctuaryCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
                borderColor = if (selected) MaterialTheme.colorScheme.primary else BrainBufferThemeTokens.extended.cardBorder,
                contentPadding = PaddingValues(16.dp),
                onClick = { onSelectDailyTarget(minutes) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Custom Target Stepper
        SanctuaryCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Custom daily target",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Fine-tune in 15 min increments",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .springClickable(onClick = { onSelectDailyTarget((dailyTargetMin - 15).coerceAtLeast(15)) }),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease target", modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        text = formatMinutesCompact(dailyTargetMin),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .springClickable(onClick = { onSelectDailyTarget((dailyTargetMin + 15).coerceAtMost(360)) }),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "Increase target",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step9Summary(
    studentName: String,
    avatar: StudentAvatar,
    studyLevel: StudyLevel,
    courseOrExam: String,
    subjects: List<Subject>,
    dailyTargetMin: Int,
    preferredFocusMin: Int,
    weeklyStudyDays: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        StudentAvatarBadge(avatar = avatar, size = 76.dp)
        Text(
            text = "Your study space is ready, $studentName.",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Everything is saved privately on your device and can be updated anytime in Profile.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        SanctuaryCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(18.dp)
        ) {
            SummaryMetricLine("Study path", buildString {
                append(studyLevel.label)
                if (courseOrExam.isNotBlank()) append(" · $courseOrExam")
            })
            Spacer(modifier = Modifier.height(10.dp))
            SummaryMetricLine("Daily focus target", formatMinutesCompact(dailyTargetMin))
            Spacer(modifier = Modifier.height(10.dp))
            SummaryMetricLine("Focus rhythm", "$preferredFocusMin min blocks · $weeklyStudyDays days/week")

            if (subjects.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Your subjects",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subjects.forEach { subject ->
                        SubjectTag(subject = subject)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepTitleHeader(
    eyebrow: String,
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectablePillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Surface(
        modifier = (if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .springClickable(onClick = onClick)
            .minimumInteractiveComponentSize(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else BrainBufferThemeTokens.extended.cardBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SummaryMetricLine(label: String, value: String) {
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
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun formatMinutesCompact(totalMinutes: Int): String {
    if (totalMinutes <= 0) return "0m"
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }
}
