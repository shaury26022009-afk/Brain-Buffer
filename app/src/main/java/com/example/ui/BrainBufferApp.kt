package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.audio.AmbientSoundscapeController
import com.example.audio.LocalAmbientSoundscapeController
import com.example.ui.components.ActivitySwitcherSheet
import com.example.ui.components.AppRoutes
import com.example.ui.components.BottomNavDestination
import com.example.ui.components.BrainBufferBottomBar
import com.example.ui.components.BrainBufferLiquidGlassRail
import com.example.ui.components.BrainBufferLivePill
import com.example.ui.components.ControlledPhoneAccessSheet
import com.example.ui.components.FocusGuardSettingsSheet
import com.example.ui.navigation.BrainBufferNavigationState
import com.example.ui.navigation.rememberBrainBufferNavigationState
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.GardenScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsStandaloneScreen
import com.example.ui.screens.MindResetScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PlannerScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsStandaloneScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StrictFocusScreen
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.LocalAdaptiveMetrics
import com.example.ui.theme.ProvideAdaptiveMetrics
import com.example.viewmodel.BrainBufferViewModel

@Composable
fun BrainBufferApp(
    viewModel: BrainBufferViewModel,
    navigationState: BrainBufferNavigationState = rememberBrainBufferNavigationState(),
    openControlledAccessRequest: Boolean = false,
    onControlledAccessRequestConsumed: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val renderQuality = BrainBufferThemeTokens.renderQuality
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val currentRoute = navigationState.currentRoute
    val currentTopLevel = navigationState.currentTopLevelDestination
    val showMainNavigation = navigationState.shouldShowMainNavigation(isZenMode = uiState.timer.isZenMode)
    val transitionDuration = renderQuality.transitionDurationMs
    var showActivitySwitcher by remember { mutableStateOf(false) }
    var showControlledPhoneAccess by remember { mutableStateOf(false) }
    var showFocusGuardSettings by remember { mutableStateOf(false) }

    LaunchedEffect(openControlledAccessRequest, uiState.preferences.isLoaded, uiState.preferences.onboardingCompleted) {
        if (openControlledAccessRequest && uiState.preferences.isLoaded && uiState.preferences.onboardingCompleted) {
            viewModel.setActivityRequestSheetOpen(true)
            showControlledPhoneAccess = true
            onControlledAccessRequestConsumed()
        }
    }

    // Offline synthesized ambient study soundscape controller synced with Focus Timer
    val soundscapeController = remember { AmbientSoundscapeController() }
    LaunchedEffect(uiState.timer.isRunning) {
        soundscapeController.onTimerRunningChanged(uiState.timer.isRunning)
    }
    DisposableEffect(soundscapeController) {
        onDispose { soundscapeController.release() }
    }

    // Single source of truth synchronized with NavController with zero frame lag.
    val lastTopLevelHolder = remember { mutableStateOf(BottomNavDestination.HOME) }
    if (currentTopLevel != null && lastTopLevelHolder.value != currentTopLevel) {
        lastTopLevelHolder.value = currentTopLevel
    }
    val activeTopLevel = currentTopLevel ?: lastTopLevelHolder.value

    val showFloatingLivePill = uiState.timer.hasStarted &&
        currentRoute != null &&
        currentRoute != AppRoutes.FOCUS &&
        currentRoute != AppRoutes.STRICT_FOCUS &&
        currentRoute != AppRoutes.SPLASH &&
        currentRoute != AppRoutes.ONBOARDING

    val activeSubjectName = uiState.subjects
        .firstOrNull { it.id == uiState.timer.selectedSubjectId }
        ?.name ?: "General Study"

    CompositionLocalProvider(
        LocalAmbientSoundscapeController provides soundscapeController
    ) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ProvideAdaptiveMetrics(maxWidth = maxWidth, maxHeight = maxHeight) {
            val adaptive = LocalAdaptiveMetrics.current
            val useRail = adaptive.useNavigationRail

            // Adaptive single-navigation shell:
            // - Tablets & landscape windows: ONE floating left Liquid Glass rail + main content pane.
            // - Compact phones & narrow split-screen: edge-to-edge content + ONE floating bottom Liquid Glass pill.
            Row(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = showMainNavigation && useRail,
                    enter = fadeIn(tween(transitionDuration)) + slideInHorizontally { -it / 2 },
                    exit = fadeOut(tween(transitionDuration)) + slideOutHorizontally { -it / 2 }
                ) {
                    BrainBufferLiquidGlassRail(
                        currentDestination = activeTopLevel,
                        onSelectDestination = navigationState::navigateToTopLevel,
                        isFocusTimerRunning = uiState.timer.isRunning,
                        hapticsEnabled = uiState.preferences.hapticsEnabled
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    NavHost(
                        navController = navigationState.navController,
                        startDestination = AppRoutes.SPLASH,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            if (reducedMotion || transitionDuration <= 0) {
                                fadeIn(animationSpec = tween(0))
                            } else {
                                fadeIn(animationSpec = tween(transitionDuration, easing = FastOutSlowInEasing)) +
                                    scaleIn(
                                        initialScale = 0.986f,
                                        animationSpec = tween(transitionDuration, easing = FastOutSlowInEasing)
                                    )
                            }
                        },
                        exitTransition = {
                            if (reducedMotion || transitionDuration <= 0) {
                                fadeOut(animationSpec = tween(0))
                            } else {
                                fadeOut(animationSpec = tween((transitionDuration * 0.85f).toInt(), easing = FastOutSlowInEasing)) +
                                    scaleOut(
                                        targetScale = 0.992f,
                                        animationSpec = tween((transitionDuration * 0.85f).toInt(), easing = FastOutSlowInEasing)
                                    )
                            }
                        },
                        popEnterTransition = {
                            if (reducedMotion || transitionDuration <= 0) {
                                fadeIn(animationSpec = tween(0))
                            } else {
                                fadeIn(animationSpec = tween(transitionDuration, easing = FastOutSlowInEasing)) +
                                    scaleIn(
                                        initialScale = 0.988f,
                                        animationSpec = tween(transitionDuration, easing = FastOutSlowInEasing)
                                    )
                            }
                        },
                        popExitTransition = {
                            if (reducedMotion || transitionDuration <= 0) {
                                fadeOut(animationSpec = tween(0))
                            } else {
                                fadeOut(animationSpec = tween((transitionDuration * 0.85f).toInt(), easing = FastOutSlowInEasing))
                            }
                        }
                    ) {
                        composable(AppRoutes.SPLASH) {
                            SplashScreen(
                                isPreferencesLoaded = uiState.preferences.isLoaded,
                                onboardingCompleted = uiState.preferences.onboardingCompleted,
                                onNavigateToOnboarding = navigationState::navigateAfterSplash,
                                onNavigateToHome = navigationState::navigateFromSplashToHome
                            )
                        }

                        composable(AppRoutes.ONBOARDING) {
                            OnboardingScreen(
                                existingPreferences = uiState.preferences,
                                existingSubjects = uiState.subjects,
                                onComplete = { studentName, avatarKey, studyLevel, courseOrExam, subjects, studyGoals, customGoal, focusMin, shortBreakMin, studyTime, weeklyDays, distractions, otherDistraction, dailyTargetMin ->
                                    viewModel.completeOnboarding(
                                        studentName = studentName,
                                        avatarKey = avatarKey,
                                        studyLevel = studyLevel,
                                        courseOrExamName = courseOrExam,
                                        subjects = subjects,
                                        studyGoals = studyGoals,
                                        customGoal = customGoal,
                                        preferredFocusMinutes = focusMin,
                                        shortBreakMinutes = shortBreakMin,
                                        preferredStudyTime = studyTime,
                                        weeklyStudyDaysTarget = weeklyDays,
                                        biggestDistractions = distractions,
                                        otherDistraction = otherDistraction,
                                        dailyTargetMinutes = dailyTargetMin,
                                        onDone = navigationState::navigateAfterOnboarding
                                    )
                                }
                            )
                        }

                        composable(AppRoutes.HOME) {
                            HomeScreen(
                                uiState = uiState,
                                greeting = viewModel.getGreeting(),
                                formattedDate = viewModel.getFormattedTodayDate(),
                                onRefreshMotivation = viewModel::nextMotivationalMessage,
                                onStartFocus = { subjectId ->
                                    if (uiState.timer.hasStarted) {
                                        if (uiState.timer.isStrictFocus) {
                                            navigationState.navigateToStrictFocus()
                                        } else {
                                            navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
                                        }
                                    } else {
                                        viewModel.startQuickFocus(subjectId)
                                        navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
                                    }
                                },
                                onOpenFocus = {
                                    if (uiState.timer.hasStarted && uiState.timer.isStrictFocus) {
                                        navigationState.navigateToStrictFocus()
                                    } else {
                                        navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
                                    }
                                },
                                onToggleTask = viewModel::toggleTaskCompletion,
                                onQuickAddTask = { title, subjectId ->
                                    viewModel.saveTask(
                                        existingId = null,
                                        title = title,
                                        subjectId = subjectId,
                                        dueDateEpochDay = uiState.todayEpochDay,
                                        dueTimeLabel = "Today",
                                        estimatedMinutes = uiState.preferences.preferredFocusMinutes,
                                        priority = com.example.model.TaskPriority.MEDIUM,
                                        notes = ""
                                    )
                                },
                                onOpenPlanner = { navigationState.navigateToTopLevel(BottomNavDestination.PLANNER) },
                                onOpenGarden = { navigationState.navigateToTopLevel(BottomNavDestination.GARDEN) },
                                onOpenInsights = navigationState::navigateToInsights,
                                onOpenMindReset = navigationState::navigateToMindReset,
                                onOpenProfile = { navigationState.navigateToTopLevel(BottomNavDestination.PROFILE) }
                            )
                        }

                        composable(AppRoutes.FOCUS) {
                            FocusScreen(
                                uiState = uiState,
                                onSelectMode = viewModel::selectFocusMode,
                                onSetDuration = viewModel::setCustomTimerDuration,
                                onSelectSubject = viewModel::selectTimerSubject,
                                onAddSubject = viewModel::saveSubject,
                                onUpdateSessionGoal = viewModel::updateSessionGoal,
                                onToggleZenMode = viewModel::toggleZenMode,
                                onStartOrResume = viewModel::startOrResumeTimer,
                                onPause = viewModel::pauseTimer,
                                onReset = viewModel::resetTimer,
                                onCompleteNow = { viewModel.completeCurrentSession(autoCompleted = false) },
                                onDismissCelebration = viewModel::dismissCelebration,
                                onOpenGarden = { navigationState.navigateToTopLevel(BottomNavDestination.GARDEN) },
                                onOpenMindReset = navigationState::navigateToMindReset,
                                onAddDistractionNote = viewModel::addDistractionNote,
                                onOpenStrictFocus = navigationState::navigateToStrictFocus,
                                onOpenActivitySwitcher = { showActivitySwitcher = true },
                                onReturnToStudy = viewModel::returnToPausedStudySession,
                                onExtendTimer = viewModel::extendActiveTimer,
                                onAcceptAppSuggestion = { suggestion ->
                                    viewModel.switchActivity(
                                        activityType = suggestion.suggestedActivity,
                                        activityLabel = suggestion.suggestedActivity.label,
                                        category = suggestion.suggestedActivity.category,
                                        durationMinutes = suggestion.suggestedDurationMinutes,
                                        subjectId = uiState.timer.selectedSubjectId,
                                        wasPlanned = false
                                    )
                                },
                                onDismissAppSuggestion = viewModel::dismissAppUsageSuggestion,
                                onOpenControlledPhoneAccess = {
                                    viewModel.setActivityRequestSheetOpen(true)
                                    showControlledPhoneAccess = true
                                },
                                onDismissUnapprovedWarning = viewModel::dismissUnapprovedAppWarning,
                                onStartBreakFromCelebration = { viewModel.startBreakFromCurrent() }
                            )
                        }

                        composable(AppRoutes.STRICT_FOCUS) {
                            StrictFocusScreen(
                                uiState = uiState,
                                onBack = navigationState::navigateBack,
                                onStartStrictFocus = { subId, goal, act, mins, strictness, dnd, aod ->
                                    viewModel.startStrictFocus(
                                        subjectId = subId,
                                        sessionGoal = goal,
                                        activityType = act,
                                        durationMinutes = mins,
                                        strictness = strictness,
                                        enableDnd = dnd,
                                        enableAodStyle = aod
                                    )
                                },
                                onPause = viewModel::pauseTimer,
                                onResume = viewModel::startOrResumeTimer,
                                onExtendFiveMin = { viewModel.extendActiveTimer(5) },
                                onToggleAodStyle = viewModel::toggleStrictFocusAod,
                                onOpenActivitySwitcher = { showActivitySwitcher = true },
                                onExitStrictFocus = viewModel::exitStrictFocus,
                                onSavePreset = viewModel::saveStrictFocusPreset,
                                onDeletePreset = viewModel::deleteStrictFocusPreset,
                                onStartFocusGuardWithLevel = { subId, goal, act, mins, strictness, dnd, aod, guardLevel ->
                                    viewModel.startStrictFocus(
                                        subjectId = subId,
                                        sessionGoal = goal,
                                        activityType = act,
                                        durationMinutes = mins,
                                        strictness = strictness,
                                        enableDnd = dnd,
                                        enableAodStyle = aod,
                                        guardLevel = guardLevel
                                    )
                                },
                                onOpenFocusGuardSettings = { showFocusGuardSettings = true },
                                onOpenControlledPhoneAccess = {
                                    viewModel.setActivityRequestSheetOpen(true)
                                    showControlledPhoneAccess = true
                                },
                                onReturnToStudy = viewModel::returnToPausedStudySession,
                                onDismissUnapprovedWarning = viewModel::dismissUnapprovedAppWarning
                            )
                        }

                        composable(AppRoutes.PLANNER) {
                            PlannerScreen(
                                uiState = uiState,
                                onToggleTask = viewModel::toggleTaskCompletion,
                                onSaveTask = viewModel::saveTask,
                                onDeleteTask = viewModel::deleteTask,
                                onUndoDelete = viewModel::undoDeleteTask
                            )
                        }

                        composable(AppRoutes.GARDEN) {
                            GardenScreen(
                                uiState = uiState,
                                onSelectActivePlant = viewModel::setActivePlant,
                                onNurtureWithSeed = viewModel::nurtureActivePlantWithSeed,
                                onPlantNewSeed = viewModel::plantNewSeed,
                                onStartFocus = { navigationState.navigateToTopLevel(BottomNavDestination.FOCUS) }
                            )
                        }

                        composable(AppRoutes.PROFILE) {
                            ProfileScreen(
                                uiState = uiState,
                                onUpdateTheme = viewModel::updateThemeMode,
                                onUpdateProfile = viewModel::updateProfile,
                                onSaveSubject = viewModel::saveSubject,
                                onDeleteSubject = viewModel::deleteSubject,
                                onUpdateTimerAndTarget = viewModel::updateTimerAndTargetPrefs,
                                onUpdateAppSettings = viewModel::updateAppSettings,
                                onOpenMindReset = navigationState::navigateToMindReset,
                                onExportStudyBackup = viewModel::buildStudyBackupJson,
                                onLoadDemoData = viewModel::loadExplicitDemoData,
                                onClearAllData = viewModel::clearAllStudyData,
                                onResetOnboarding = {
                                    viewModel.restartOnboarding(onReady = navigationState::replayOnboarding)
                                },
                                onUpdatePerformanceOverride = viewModel::updatePerformanceOverride,
                                onUpdateNotificationPrefs = viewModel::updateNotificationPreferences,
                                onUpdateAppUsageAwareness = viewModel::updateAppUsageAwareness
                            )
                        }

                        composable(AppRoutes.INSIGHTS) {
                            InsightsStandaloneScreen(
                                uiState = uiState,
                                onBack = navigationState::navigateBack
                            )
                        }

                        composable(AppRoutes.MIND_RESET) {
                            MindResetScreen(
                                uiState = uiState,
                                onBack = navigationState::navigateBack,
                                onCycleCompleted = viewModel::recordMindResetBreathCycle,
                                onAddDistractionNote = viewModel::addDistractionNote,
                                onAddReflection = viewModel::addSessionReflection,
                                onDeleteMindNote = viewModel::deleteMindNote,
                                onReturnToFocus = {
                                    navigationState.navigateBack()
                                    navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
                                }
                            )
                        }

                        composable(AppRoutes.SETTINGS) {
                            SettingsStandaloneScreen(
                                uiState = uiState,
                                onBack = navigationState::navigateBack,
                                onUpdateTheme = viewModel::updateThemeMode,
                                onUpdateTimerAndTarget = viewModel::updateTimerAndTargetPrefs,
                                onUpdateAppSettings = viewModel::updateAppSettings,
                                onExportStudyBackup = viewModel::buildStudyBackupJson,
                                onLoadDemoData = viewModel::loadExplicitDemoData,
                                onClearAllData = viewModel::clearAllStudyData,
                                onResetOnboarding = {
                                    viewModel.restartOnboarding(onReady = navigationState::replayOnboarding)
                                },
                                onUpdatePerformanceOverride = viewModel::updatePerformanceOverride,
                                onUpdateNotificationPrefs = viewModel::updateNotificationPreferences,
                                onUpdateAppUsageAwareness = viewModel::updateAppUsageAwareness
                            )
                        }
                    }

                    // BRAIN BUFFER LIVE: Floating Dynamic Island-style in-app live timer pill
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showFloatingLivePill,
                        modifier = Modifier.align(Alignment.TopCenter),
                        enter = fadeIn(tween(transitionDuration)) + slideInVertically { -it / 2 },
                        exit = fadeOut(tween(transitionDuration)) + slideOutVertically { -it / 2 }
                    ) {
                        BrainBufferLivePill(
                            timer = uiState.timer,
                            subjectName = activeSubjectName,
                            onPause = viewModel::pauseTimer,
                            onResume = viewModel::startOrResumeTimer,
                            onOpenFocus = {
                                if (uiState.timer.isStrictFocus) {
                                    navigationState.navigateToStrictFocus()
                                } else {
                                    navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
                                }
                            },
                            onSwitchActivity = {
                                viewModel.setActivityRequestSheetOpen(true)
                                showControlledPhoneAccess = true
                            },
                            onReturnToStudy = viewModel::returnToPausedStudySession,
                            onExtendFiveMin = { viewModel.extendActiveTimer(5) },
                            onEndSession = viewModel::resetTimer
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showMainNavigation && !useRail,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = fadeIn(tween(transitionDuration)) + slideInVertically { it / 2 },
                        exit = fadeOut(tween(transitionDuration)) + slideOutVertically { it / 2 }
                    ) {
                        BrainBufferBottomBar(
                            currentDestination = activeTopLevel,
                            onSelectDestination = navigationState::navigateToTopLevel,
                            isFocusTimerRunning = uiState.timer.isRunning,
                            hapticsEnabled = uiState.preferences.hapticsEnabled
                        )
                    }
                }
            }
        }
    }
    }

    if (showActivitySwitcher) {
        ActivitySwitcherSheet(
            subjects = uiState.subjects,
            customActivities = uiState.customActivities,
            selectedSubjectId = uiState.timer.selectedSubjectId,
            hasActiveStudySession = uiState.timer.hasStarted && uiState.timer.activeActivityCategory.countsAsFocusedStudy,
            onDismiss = { showActivitySwitcher = false },
            onConfirmSwitch = { type, label, category, durationMinutes, subjectId, wasPlanned ->
                viewModel.switchActivity(
                    activityType = type,
                    activityLabel = label,
                    category = category,
                    durationMinutes = durationMinutes,
                    subjectId = subjectId,
                    wasPlanned = wasPlanned
                )
                showActivitySwitcher = false
            },
            onAddCustomActivity = viewModel::addCustomActivity,
            onDeleteCustomActivity = viewModel::deleteCustomActivity
        )
    }

    if (showControlledPhoneAccess) {
        ControlledPhoneAccessSheet(
            allowances = uiState.preferences.focusGuardSettings.allowances,
            appCategoryMappings = uiState.appCategoryMappings,
            todayEntertainmentMinutes = uiState.todayEntertainmentMinutes,
            hasActiveStudySession = uiState.timer.hasStarted && uiState.timer.activeActivityCategory.countsAsFocusedStudy,
            hapticsEnabled = uiState.preferences.hapticsEnabled && uiState.preferences.focusGuardSettings.vibrationEnabled,
            onDismiss = {
                showControlledPhoneAccess = false
                viewModel.setActivityRequestSheetOpen(false)
            },
            onApproveActivity = { type, label, category, durationMinutes, pkg, appLabel, docUri ->
                viewModel.switchActivity(
                    activityType = type,
                    activityLabel = label,
                    category = category,
                    durationMinutes = durationMinutes,
                    subjectId = uiState.timer.selectedSubjectId,
                    wasPlanned = true,
                    approvedPackageName = pkg,
                    approvedAppLabel = appLabel,
                    approvedDocumentUri = docUri
                )
                showControlledPhoneAccess = false
                viewModel.setActivityRequestSheetOpen(false)
            }
        )
    }

    if (showFocusGuardSettings) {
        FocusGuardSettingsSheet(
            settings = uiState.preferences.focusGuardSettings,
            appUsageAwarenessEnabled = uiState.preferences.appUsageAwarenessEnabled,
            appCategoryMappings = uiState.appCategoryMappings,
            onUpdateSettings = viewModel::updateFocusGuardSettings,
            onUpdateAppUsageAwareness = viewModel::updateAppUsageAwareness,
            onUpsertAppMapping = viewModel::upsertAppCategoryMapping,
            onDeleteAppMapping = viewModel::deleteAppCategoryMapping,
            onSaveStarterPreset = viewModel::saveStrictFocusPreset,
            onDismiss = { showFocusGuardSettings = false }
        )
    }
}
