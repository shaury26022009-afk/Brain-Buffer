package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.audio.LocalAmbientSoundscapeController
import com.example.domain.FocusTimerEngine
import com.example.model.BlockedAppCategory
import com.example.model.BlockedAppRule
import com.example.model.SessionPurposeCategory
import com.example.notifications.BrainBufferNotificationManager
import com.example.performance.PerformanceProfile
import com.example.system.PhoneUsageAnalyticsEngine
import com.example.ui.components.AppRoutes
import com.example.ui.components.BottomNavDestination
import com.example.ui.components.BrainBufferBottomBar
import com.example.ui.components.BrainBufferLiquidGlassRail
import com.example.ui.components.BrainBufferLivePill
import com.example.ui.navigation.BrainBufferNavigationState
import com.example.ui.navigation.rememberBrainBufferNavigationState
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ExitSessionConfirmationDialog
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsPrimaryScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.LocalAdaptiveMetrics
import com.example.ui.theme.ProvideAdaptiveMetrics
import com.example.viewmodel.BrainBufferViewModel

/**
 * Root shell for BRAIN BUFFER — Purpose-Aware Focus System.
 *
 * Exact 4 primary destinations:
 * 1. Home
 * 2. Focus (centre of the product)
 * 3. Insights (Intent vs Reality + separate Device Screen Time)
 * 4. Settings (Settings & Profile)
 */
@Composable
fun BrainBufferApp(
    viewModel: BrainBufferViewModel,
    navigationState: BrainBufferNavigationState = rememberBrainBufferNavigationState(),
    openControlledAccessRequest: Boolean = false,
    onControlledAccessRequestConsumed: () -> Unit = {},
    openChangePurposeRequest: Boolean = false,
    onChangePurposeRequestConsumed: () -> Unit = {},
    foregroundResumeSignal: Int = 0
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val renderQuality = BrainBufferThemeTokens.renderQuality
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val context = LocalContext.current
    val currentRoute = navigationState.currentRoute
    val currentTopLevel = navigationState.currentTopLevelDestination
    val isDedicatedFocusMode = uiState.timer.hasStarted && currentRoute == AppRoutes.FOCUS
    val showMainNavigation = navigationState.shouldShowMainNavigation(
        isZenMode = uiState.timer.isZenMode || isDedicatedFocusMode
    )
    val transitionDuration = renderQuality.transitionDurationMs

    var triggerAppSwitchInFocus by remember { mutableStateOf(false) }
    var triggerChangePurposeInFocus by remember { mutableStateOf(false) }
    var showLivePillExitDialog by remember { mutableStateOf(false) }

    var pendingStartAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var hasPromptedNotificationPermission by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingStartAction?.invoke()
        pendingStartAction = null
    }

    val runWithNotificationPermissionCheck: (() -> Unit) -> Unit = { action ->
        val needsPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPromptedNotificationPermission &&
            uiState.preferences.notificationsEnabled &&
            !BrainBufferNotificationManager.hasNotificationPermission(context)
        if (needsPrompt) {
            hasPromptedNotificationPermission = true
            pendingStartAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    LaunchedEffect(openControlledAccessRequest, uiState.preferences.isLoaded, uiState.preferences.onboardingCompleted) {
        if (openControlledAccessRequest && uiState.preferences.isLoaded && uiState.preferences.onboardingCompleted) {
            navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
            triggerAppSwitchInFocus = true
            onControlledAccessRequestConsumed()
        }
    }

    LaunchedEffect(openChangePurposeRequest, uiState.preferences.isLoaded, uiState.preferences.onboardingCompleted) {
        if (openChangePurposeRequest && uiState.preferences.isLoaded && uiState.preferences.onboardingCompleted) {
            navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
            triggerChangePurposeInFocus = true
            onChangePurposeRequestConsumed()
        }
    }

    // Automatically enter dedicated Focus Mode whenever a session starts.
    var previousHasStarted by remember { mutableStateOf(uiState.timer.hasStarted) }
    LaunchedEffect(uiState.timer.hasStarted, uiState.preferences.onboardingCompleted) {
        if (uiState.timer.hasStarted && !previousHasStarted && uiState.preferences.onboardingCompleted) {
            if (currentRoute !in setOf(AppRoutes.SPLASH, AppRoutes.ONBOARDING, AppRoutes.AUTH, AppRoutes.FOCUS)) {
                navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
            }
        }
        previousHasStarted = uiState.timer.hasStarted
    }

    // Automatically return to active Focus Mode when the user unlocks the phone or re-enters
    // the app from background while a focus session is active.
    LaunchedEffect(
        foregroundResumeSignal,
        uiState.preferences.isLoaded,
        uiState.preferences.onboardingCompleted
    ) {
        if (foregroundResumeSignal > 0 &&
            uiState.preferences.isLoaded &&
            uiState.preferences.onboardingCompleted &&
            uiState.timer.hasStarted &&
            currentRoute !in setOf(AppRoutes.SPLASH, AppRoutes.ONBOARDING, AppRoutes.AUTH, AppRoutes.FOCUS)
        ) {
            navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
        }
    }

    // Offline synthesized ambient soundscape hosted in ViewModel so playback survives
    // configuration changes, screen navigation, and persists across app restarts.
    val soundscapeController = viewModel.soundscapeController

    val lastTopLevelHolder = remember { mutableStateOf(BottomNavDestination.HOME) }
    if (currentTopLevel != null && lastTopLevelHolder.value != currentTopLevel) {
        lastTopLevelHolder.value = currentTopLevel
    }
    val activeTopLevel = currentTopLevel ?: lastTopLevelHolder.value

    val showFloatingLivePill = uiState.timer.hasStarted &&
        currentRoute != null &&
        currentRoute != AppRoutes.FOCUS &&
        currentRoute != AppRoutes.SPLASH &&
        currentRoute != AppRoutes.ONBOARDING &&
        currentRoute != AppRoutes.AUTH

    val activeSessionTitle = uiState.timer.sessionGoal.ifBlank {
        uiState.timer.snapshot.purposeCategory.label
    }

    CompositionLocalProvider(
        LocalAmbientSoundscapeController provides soundscapeController,
        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ProvideAdaptiveMetrics(maxWidth = maxWidth, maxHeight = maxHeight) {
                val adaptive = LocalAdaptiveMetrics.current
                val useRail = adaptive.useNavigationRail

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
                        val isPerformanceProfile = renderQuality.profile == PerformanceProfile.PERFORMANCE
                        fun routeTabIndex(route: String?): Int {
                            return BottomNavDestination.entries.indexOfFirst { it.route == route }
                        }

                        NavHost(
                            navController = navigationState.navController,
                            startDestination = AppRoutes.SPLASH,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = {
                                if (reducedMotion || transitionDuration <= 0) {
                                    fadeIn(animationSpec = tween(0))
                                } else if (isPerformanceProfile) {
                                    fadeIn(animationSpec = tween(140, easing = FastOutSlowInEasing))
                                } else {
                                    val fromIdx = routeTabIndex(initialState.destination.route)
                                    val toIdx = routeTabIndex(targetState.destination.route)
                                    val dur = transitionDuration.coerceIn(180, 260)
                                    val fade = fadeIn(animationSpec = tween(dur, easing = FastOutSlowInEasing))
                                    val subtleScale = scaleIn(
                                        initialScale = 0.988f,
                                        animationSpec = tween(dur, easing = FastOutSlowInEasing)
                                    )
                                    if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                                        val dir = if (toIdx > fromIdx) 1 else -1
                                        fade + subtleScale + slideInHorizontally(
                                            animationSpec = tween(dur, easing = FastOutSlowInEasing)
                                        ) { fullWidth -> (fullWidth * 0.08f * dir).toInt() }
                                    } else {
                                        fade + subtleScale
                                    }
                                }
                            },
                            exitTransition = {
                                if (reducedMotion || transitionDuration <= 0) {
                                    fadeOut(animationSpec = tween(0))
                                } else if (isPerformanceProfile) {
                                    fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing))
                                } else {
                                    val dur = (transitionDuration * 0.85f).toInt().coerceIn(140, 220)
                                    fadeOut(animationSpec = tween(dur, easing = FastOutSlowInEasing)) +
                                        scaleOut(
                                            targetScale = 0.992f,
                                            animationSpec = tween(dur, easing = FastOutSlowInEasing)
                                        )
                                }
                            }
                        ) {
                            composable(AppRoutes.SPLASH) {
                                SplashScreen(
                                    isPreferencesLoaded = uiState.preferences.isLoaded,
                                    onboardingCompleted = uiState.preferences.onboardingCompleted,
                                    onNavigateToOnboarding = navigationState::navigateAfterSplash,
                                    onNavigateToHome = {
                                        if (uiState.timer.hasStarted) {
                                            navigationState.navigateFromSplashToFocus()
                                        } else {
                                            navigationState.navigateFromSplashToHome()
                                        }
                                    }
                                )
                            }

                            composable(AppRoutes.ONBOARDING) {
                                OnboardingScreen(
                                    existingPreferences = uiState.preferences,
                                    existingSubjects = uiState.subjects,
                                    existingBlockedApps = uiState.blockedApps,
                                    appCategoryMappings = uiState.appCategoryMappings,
                                    cloudSync = uiState.cloudSync,
                                    onStepChanged = viewModel::saveOnboardingCurrentStep,
                                    onSaveDraft = viewModel::saveOnboardingDraft,
                                    onSignIn = { email, password ->
                                        viewModel.signInWithEmail(email, password) {}
                                    },
                                    onSignUp = { email, password, displayName ->
                                        viewModel.signUpWithEmail(email, password, displayName) {}
                                    },
                                    onResetPassword = viewModel::resetPasswordEmail,
                                    onResendVerification = viewModel::resendVerificationEmail,
                                    onClearAuthNotice = viewModel::clearAuthNotice,
                                    onCompleteFull = { role, studentName, avatarKey, studyLevel, courseOrExam, subjects, studyGoals, customGoal, focusMin, shortBreakMin, longBreakMin, studyTime, weeklyDays, distractions, otherDistraction, dailyTargetMin, fgSettings, notifPrefs, blockedRules, usageAwareness ->
                                        viewModel.completeFullOnboarding(
                                            userRole = role,
                                            studentName = studentName,
                                            avatarKey = avatarKey,
                                            studyLevel = studyLevel,
                                            courseOrExamName = courseOrExam,
                                            subjects = subjects,
                                            studyGoals = studyGoals,
                                            customGoal = customGoal,
                                            preferredFocusMinutes = focusMin,
                                            shortBreakMinutes = shortBreakMin,
                                            longBreakMinutes = longBreakMin,
                                            preferredStudyTime = studyTime,
                                            weeklyStudyDaysTarget = weeklyDays,
                                            biggestDistractions = distractions,
                                            otherDistraction = otherDistraction,
                                            dailyTargetMinutes = dailyTargetMin,
                                            focusGuardSettings = fgSettings,
                                            notificationPrefs = notifPrefs,
                                            selectedBlockedApps = blockedRules,
                                            appUsageAwarenessEnabled = usageAwareness,
                                            onDone = navigationState::navigateAfterOnboarding
                                        )
                                    }
                                )
                            }

                            // 1. HOME
                            composable(AppRoutes.HOME) {
                                HomeScreen(
                                    uiState = uiState,
                                    onOpenFocus = {
                                        navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
                                    },
                                    onSelectQuickPurpose = { category ->
                                        viewModel.preselectPurposeForSetup(category)
                                        navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
                                    },
                                    onOpenInsights = navigationState::navigateToInsights,
                                    onOpenSettings = navigationState::navigateToSettings,
                                    onToggleTaskCompleted = { task -> viewModel.toggleTaskCompletion(task.id) },
                                    onQuickAddTask = { title ->
                                        viewModel.saveTask(
                                            existingId = null,
                                            title = title,
                                            subjectId = null,
                                            dueDateEpochDay = uiState.todayEpochDay,
                                            dueTimeLabel = "Today",
                                            estimatedMinutes = uiState.preferences.preferredFocusMinutes,
                                            priority = com.example.model.TaskPriority.MEDIUM,
                                            notes = ""
                                        )
                                    },
                                    onFocusTask = { task ->
                                        viewModel.preselectPurposeForSetup(SessionPurposeCategory.STUDY)
                                        viewModel.selectLinkedTask(task.id)
                                        navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
                                    }
                                )
                            }

                            // 2. FOCUS (Centre of product)
                            composable(AppRoutes.FOCUS) {
                                FocusScreen(
                                    uiState = uiState,
                                    onStartPurposeSession = { purpose, name, durationMin, selectedApps, breakPlan, linkedTaskId ->
                                        runWithNotificationPermissionCheck {
                                            viewModel.startPurposeFocusSession(
                                                purposeCategory = purpose,
                                                sessionName = name,
                                                durationMinutes = durationMin,
                                                selectedApps = selectedApps,
                                                breakPlan = breakPlan,
                                                linkedTaskId = linkedTaskId
                                            )
                                        }
                                    },
                                    onTogglePauseResume = {
                                        if (uiState.timer.isRunning) {
                                            viewModel.togglePauseResume()
                                        } else {
                                            runWithNotificationPermissionCheck {
                                                viewModel.togglePauseResume()
                                            }
                                        }
                                    },
                                    onStartTempAppSwitch = { pkg, label, mins ->
                                        viewModel.startTemporaryAppSwitch(
                                            packageName = pkg,
                                            appLabel = label,
                                            durationMinutes = mins,
                                            launchAfterGrant = true
                                        )
                                    },
                                    onRequestTempAccessExtension = { mins ->
                                        viewModel.requestTemporaryAccessExtension(mins)
                                    },
                                    onReturnToFocusFromTempAccess = viewModel::returnToFocusFromTemporaryAccess,
                                    onStartBreak = { mins -> viewModel.startBreakFromCurrent(mins) },
                                    onReturnFromBreak = viewModel::returnToPausedStudySession,
                                    onFinishEarly = viewModel::endSessionEarly,
                                    onCancelSession = viewModel::resetTimer,
                                    onSwitchPurpose = viewModel::switchActiveSessionPurpose,
                                    onDistractionAction = { pkg, label, action ->
                                        viewModel.recordDistractionMismatchAction(
                                            packageName = pkg,
                                            appLabel = label,
                                            actionTaken = action
                                        )
                                    },
                                    onDismissCelebration = viewModel::dismissCelebration,
                                    onClearTempAccessNotice = viewModel::clearTempAccessNotice,
                                    openAppSwitchImmediately = triggerAppSwitchInFocus,
                                    onAppSwitchRequestConsumed = { triggerAppSwitchInFocus = false },
                                    openChangePurposeImmediately = triggerChangePurposeInFocus,
                                    onChangePurposeRequestConsumed = { triggerChangePurposeInFocus = false },
                                    onUpdateDailyTargetMinutes = { goal ->
                                        viewModel.updateTimerAndTargetPrefs(
                                            preferredFocusMinutes = uiState.preferences.preferredFocusMinutes,
                                            shortBreakMinutes = uiState.preferences.shortBreakMinutes,
                                            longBreakMinutes = uiState.preferences.longBreakMinutes,
                                            dailyTargetMinutes = goal
                                        )
                                    },
                                    onMinimizeFocus = {
                                        navigationState.navigateToTopLevel(BottomNavDestination.HOME)
                                    }
                                )
                            }

                            // 3. INSIGHTS (Intent vs Reality + separate Device Screen Time)
                            val toggleBlockFromUsage: (com.example.model.AppUsageEntry, Boolean) -> Unit = { appEntry, shouldBlock ->
                                if (shouldBlock) {
                                    val suggestedCat = PhoneUsageAnalyticsEngine.suggestBlockedCategory(
                                        appEntry.packageName,
                                        appEntry.appName
                                    ) ?: BlockedAppCategory.OTHER
                                    viewModel.upsertBlockedApp(
                                        BlockedAppRule(
                                            packageName = appEntry.packageName,
                                            appLabel = appEntry.appName,
                                            category = suggestedCat,
                                            blockInNormal = false,
                                            blockInHigh = true,
                                            blockInCustom = true,
                                            isBlocked = true
                                        )
                                    )
                                } else {
                                    viewModel.removeBlockedApp(appEntry.packageName)
                                }
                            }

                            composable(AppRoutes.INSIGHTS) {
                                InsightsPrimaryScreen(
                                    uiState = uiState,
                                    onRefreshUsage = { viewModel.refreshPhoneUsageAnalytics() },
                                    onSelectPeriodMode = viewModel::setUsagePeriodMode,
                                    onSelectCustomRange = viewModel::setUsageCustomDateRange,
                                    onToggleBlockApp = toggleBlockFromUsage,
                                    onOpenFocusGuardSettings = navigationState::navigateToSettings
                                )
                            }

                            // 4. SETTINGS / PROFILE (Top-level destination)
                            composable(AppRoutes.PROFILE) {
                                ProfileScreen(
                                    uiState = uiState,
                                    onUpdateTheme = viewModel::updateThemeMode,
                                    onUpdateFocusGuardSettings = viewModel::updateFocusGuardSettings,
                                    onUpdateTimerAndTarget = viewModel::updateTimerAndTargetPrefs,
                                    onUpdateAppSettings = viewModel::updateAppSettings,
                                    onUpdateNotificationPrefs = viewModel::updateNotificationPreferences,
                                    onUpdateAppUsageAwareness = viewModel::updateAppUsageAwareness,
                                    onExportStudyBackup = viewModel::buildStudyBackupJson,
                                    onClearAllData = viewModel::clearAllStudyData,
                                    onResetOnboarding = {
                                        viewModel.restartOnboarding(onReady = navigationState::replayOnboarding)
                                    },
                                    onOpenAuth = navigationState::navigateToAuth,
                                    onSyncNow = viewModel::syncCloudNow,
                                    onSignOut = viewModel::signOutCloud,
                                    onDeleteAccount = viewModel::deleteCloudAccount
                                )
                            }

                            composable(AppRoutes.AUTH) {
                                AuthScreen(
                                    cloudSync = uiState.cloudSync,
                                    defaultDisplayName = uiState.preferences.displayName,
                                    onSignIn = { email, password ->
                                        viewModel.signInWithEmail(email, password) {
                                            navigationState.navigateBack()
                                        }
                                    },
                                    onSignUp = { email, password, displayName ->
                                        viewModel.signUpWithEmail(email, password, displayName) {
                                            navigationState.navigateBack()
                                        }
                                    },
                                    onResetPassword = viewModel::resetPasswordEmail,
                                    onResendVerification = viewModel::resendVerificationEmail,
                                    onClearNotice = viewModel::clearAuthNotice,
                                    onNavigateBack = navigationState::navigateBack
                                )
                            }
                        }

                        // Floating in-app live timer pill when browsing Home/Insights/Settings during active session
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showFloatingLivePill,
                            modifier = Modifier.align(Alignment.TopCenter),
                            enter = fadeIn(tween(transitionDuration)) + slideInVertically { -it / 2 },
                            exit = fadeOut(tween(transitionDuration)) + slideOutVertically { -it / 2 }
                        ) {
                            BrainBufferLivePill(
                                timer = uiState.timer,
                                subjectName = activeSessionTitle,
                                onPause = viewModel::pauseTimer,
                                onResume = viewModel::startOrResumeTimer,
                                onOpenFocus = {
                                    navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
                                },
                                onSwitchActivity = {
                                    navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
                                    triggerAppSwitchInFocus = true
                                },
                                onReturnToStudy = viewModel::returnToPausedStudySession,
                                onExtendFiveMin = { viewModel.extendActiveTimer(5) },
                                onEndSession = { showLivePillExitDialog = true }
                            )
                        }

                        if (showLivePillExitDialog && uiState.timer.hasStarted) {
                            val exitPreviewSnap = remember(
                                uiState.timer.snapshot,
                                uiState.activeTempAccess,
                                uiState.nowEpochMs,
                                uiState.nowElapsedMs
                            ) {
                                FocusTimerEngine.resolveParentForEarlyExit(
                                    snapshot = uiState.timer.snapshot,
                                    activeTempAccess = uiState.activeTempAccess,
                                    nowEpochMs = uiState.nowEpochMs,
                                    nowElapsedRealtimeMs = uiState.nowElapsedMs
                                )
                            }
                            val previewElapsedSec = FocusTimerEngine.actualFocusedSeconds(
                                snapshot = exitPreviewSnap,
                                nowEpochMs = uiState.nowEpochMs,
                                nowElapsedRealtimeMs = uiState.nowElapsedMs
                            )
                            ExitSessionConfirmationDialog(
                                sessionName = exitPreviewSnap.sessionGoal.ifBlank { exitPreviewSnap.purposeCategory.label },
                                elapsedFormatted = BrainBufferNotificationManager.formatMinSec(previewElapsedSec),
                                plannedMinutes = (exitPreviewSnap.totalSeconds / 60).coerceAtLeast(1),
                                onFinishEarly = {
                                    showLivePillExitDialog = false
                                    viewModel.endSessionEarly()
                                },
                                onCancelSession = {
                                    showLivePillExitDialog = false
                                    viewModel.resetTimer()
                                },
                                onDismiss = { showLivePillExitDialog = false }
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
}
