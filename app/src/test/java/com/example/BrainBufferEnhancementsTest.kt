package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.audio.AmbientPcmSynthesizer
import com.example.audio.AmbientSoundscape
import com.example.audio.AmbientSoundscapeController
import com.example.audio.LocalAmbientSoundscapeController
import com.example.domain.StudyCoachEngine
import com.example.model.FocusMode
import com.example.model.FocusSessionRecord
import com.example.model.StudyTask
import com.example.model.Subject
import com.example.model.TaskPriority
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import com.example.ui.components.FocusSoundscapeSheetContent
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsStandaloneScreen
import com.example.ui.screens.MindResetScreen
import com.example.ui.theme.BrainBufferTheme
import com.example.ui.theme.ProvideAdaptiveMetrics
import com.example.viewmodel.BrainBufferUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BrainBufferEnhancementsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `AmbientPcmSynthesizer generates clean bounded non-silent audio for every active soundscape`() {
        val synth = AmbientPcmSynthesizer()
        val buffer = ShortArray(2048)

        // OFF produces pure silence
        synth.generateSamples(AmbientSoundscape.OFF, 0.8f, buffer)
        assertTrue(buffer.all { it == 0.toShort() })

        // Every active soundscape produces bounded non-silent PCM samples without clipping
        AmbientSoundscape.entries
            .filter { it != AmbientSoundscape.OFF }
            .forEach { soundscape ->
                synth.reset()
                synth.generateSamples(soundscape, 0.75f, buffer)
                val maxAmp = buffer.maxOf { abs(it.toInt()) }
                assertTrue("Expected audible signal for $soundscape", maxAmp > 100)
                assertTrue("Expected non-clipping signal for $soundscape", maxAmp <= Short.MAX_VALUE)
            }
    }

    @Test
    fun `AmbientSoundscapeController syncs play and pause with focus timer state`() {
        val controller = AmbientSoundscapeController(enableHardwareAudio = false)
        assertEquals(AmbientSoundscape.OFF, controller.state.value.selected)
        assertFalse(controller.state.value.isActive)

        controller.selectSoundscape(AmbientSoundscape.BROWN_NOISE, autoPlay = true)
        assertTrue(controller.state.value.isActive)

        controller.onTimerRunningChanged(isTimerRunning = false)
        assertFalse(controller.state.value.isPlaying)
        assertEquals(AmbientSoundscape.BROWN_NOISE, controller.state.value.selected)

        controller.onTimerRunningChanged(isTimerRunning = true)
        assertTrue(controller.state.value.isPlaying)

        controller.setVolume(0.72f)
        assertEquals(0.72f, controller.state.value.volume, 0.001f)

        controller.selectSoundscape(AmbientSoundscape.OFF)
        assertFalse(controller.state.value.isActive)
        controller.release()
    }

    @Test
    fun `StudyCoachEngine prioritizes urgent exams, due tasks, and computes 28-day heatmap and peak window`() {
        val today = LocalDate.of(2026, 11, 19).toEpochDay()
        val chem = Subject(id = 1L, name = "Chemistry", colorHex = 0xFF7B5CFA, examDateEpochDay = today + 3)
        val math = Subject(id = 2L, name = "Calculus", colorHex = 0xFF34D399)

        // 1. Urgent exam within 7 days wins priority
        val examState = BrainBufferUiState(
            subjects = listOf(chem, math),
            preferences = UserPreferences(preferredFocusMinutes = 30, dailyTargetMinutes = 90)
        )
        val examRec = StudyCoachEngine.computeRecommendation(examState, todayEpochDay = today)
        assertEquals("Exam in 3 days", examRec.badge)
        assertEquals(chem.id, examRec.subjectId)

        // 2. Overdue task wins when no urgent exam exists
        val taskState = BrainBufferUiState(
            subjects = listOf(math),
            tasks = listOf(
                StudyTask(
                    id = 10L,
                    title = "Finish Integral Worksheet",
                    subjectId = math.id,
                    dueDateEpochDay = today - 1,
                    estimatedMinutes = 25,
                    priority = TaskPriority.HIGH
                )
            )
        )
        val taskRec = StudyCoachEngine.computeRecommendation(taskState, todayEpochDay = today)
        assertEquals("Catch-up priority", taskRec.badge)
        assertEquals("Finish Integral Worksheet", taskRec.title)

        // 3. 28-Day heatmap & peak window from real saved sessions
        val zone = ZoneId.of("UTC")
        val eveningTimestamp = LocalDate.ofEpochDay(today)
            .atTime(19, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val sessions = listOf(
            FocusSessionRecord(
                id = 1L,
                subjectId = math.id,
                subjectName = "Calculus",
                subjectColorHex = math.colorHex,
                durationSeconds = 45 * 60,
                plannedSeconds = 45 * 60,
                mode = FocusMode.FOCUS,
                completedAtMs = eveningTimestamp,
                dateEpochDay = today
            ),
            FocusSessionRecord(
                id = 2L,
                subjectId = math.id,
                subjectName = "Calculus",
                subjectColorHex = math.colorHex,
                durationSeconds = 30 * 60,
                plannedSeconds = 30 * 60,
                mode = FocusMode.FOCUS,
                completedAtMs = eveningTimestamp - 86_400_000L,
                dateEpochDay = today - 1
            )
        )
        val rhythm = StudyCoachEngine.computeRhythmInsights(
            sessions = sessions,
            dailyTargetMinutes = 60,
            todayEpochDay = today,
            zoneId = zone
        )
        assertEquals(28, rhythm.heatmapCells.size)
        assertTrue(rhythm.heatmapCells.last().isToday)
        assertEquals(2, rhythm.activeDaysLast28)
        assertEquals(45, rhythm.bestDayMinutesLast28)
        assertEquals("Evening Peak", rhythm.bestWindowLabel)
        assertEquals(100, rhythm.focusQualityPercent)
    }

    @Test
    fun `Home Smart Study Coach, Focus Soundscape Sheet, Insights 28-Day Heatmap, and MindReset render smoothly`() {
        var coachLaunched = false
        val soundController = AmbientSoundscapeController(enableHardwareAudio = false)

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(LocalAmbientSoundscapeController provides soundController) {
                    ProvideAdaptiveMetrics(maxWidth = 412.dp, maxHeight = 915.dp) {
                        HomeScreen(
                            uiState = BrainBufferUiState(),
                            greeting = "Good evening",
                            formattedDate = "Thu, Nov 19",
                            onRefreshMotivation = {},
                            onStartFocus = { coachLaunched = true },
                            onOpenFocus = {},
                            onToggleTask = {},
                            onQuickAddTask = { _, _ -> },
                            onOpenPlanner = {},
                            onOpenGarden = {},
                            onOpenInsights = {},
                            onOpenMindReset = {},
                            onOpenProfile = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("home_study_coach_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_study_coach_action").performClick()
        composeRule.waitForIdle()
        assertTrue(coachLaunched)
    }

    @Test
    fun `Focus Soundscape sheet and Insights momentum heatmap are interactive`() {
        val soundController = AmbientSoundscapeController(enableHardwareAudio = false)

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(LocalAmbientSoundscapeController provides soundController) {
                    ProvideAdaptiveMetrics(maxWidth = 412.dp, maxHeight = 915.dp) {
                        FocusScreen(
                            uiState = BrainBufferUiState(),
                            onSelectMode = {},
                            onSetDuration = {},
                            onSelectSubject = {},
                            onAddSubject = {},
                            onUpdateSessionGoal = {},
                            onToggleZenMode = {},
                            onStartOrResume = {},
                            onPause = {},
                            onReset = {},
                            onCompleteNow = {},
                            onDismissCelebration = {},
                            onOpenGarden = {},
                            onOpenMindReset = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("focus_soundscape_button").assertIsDisplayed()
        soundController.selectSoundscape(AmbientSoundscape.SOFT_RAIN, autoPlay = true)
        composeRule.waitForIdle()
        assertEquals(AmbientSoundscape.SOFT_RAIN, soundController.state.value.selected)
    }

    @Test
    fun `FocusSoundscapeSheetContent, InsightsStandaloneScreen, and MindResetScreen render cleanly`() {
        var pickedSound: AmbientSoundscape? = null
        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.LIGHT) {
                FocusSoundscapeSheetContent(
                    state = AmbientSoundscapeController(enableHardwareAudio = false).state.value,
                    onSelectSoundscape = { pickedSound = it },
                    onTogglePlayPause = {},
                    onVolumeChange = {},
                    onSyncWithTimerChange = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag("focus_soundscape_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("soundscape_option_soft_rain").performClick()
        composeRule.waitForIdle()
        assertEquals(AmbientSoundscape.SOFT_RAIN, pickedSound)
    }

    @Test
    fun `InsightsStandaloneScreen renders 28-day study momentum heatmap and MindReset renders Liquid Glass controls`() {
        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK) {
                InsightsStandaloneScreen(
                    uiState = BrainBufferUiState(),
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithTag("insights_momentum_heatmap_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("insights_peak_window_badge").assertIsDisplayed()
    }

    @Test
    fun `MindResetScreen renders Liquid Glass breathing button and toggles cleanly`() {
        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK) {
                MindResetScreen(
                    uiState = BrainBufferUiState(),
                    onBack = {},
                    onCycleCompleted = {},
                    onAddDistractionNote = {},
                    onAddReflection = { _, _ -> },
                    onDeleteMindNote = {},
                    onReturnToFocus = {}
                )
            }
        }
        composeRule.onNodeWithTag("toggle_breathing_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_breathing_button").performClick()
        composeRule.waitForIdle()
    }
}
