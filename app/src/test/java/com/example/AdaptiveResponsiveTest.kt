package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.model.ThemeMode
import com.example.ui.components.BottomNavDestination
import com.example.ui.components.BrainBufferBottomBar
import com.example.ui.components.BrainBufferLiquidGlassRail
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsPrimaryScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.BrainBufferTheme
import com.example.ui.theme.LocalAdaptiveMetrics
import com.example.ui.theme.ProvideAdaptiveMetrics
import com.example.ui.theme.WindowHeightClass
import com.example.ui.theme.WindowWidthClass
import com.example.ui.theme.computeAdaptiveMetrics
import com.example.viewmodel.BrainBufferUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdaptiveResponsiveTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `window size class breakpoints cover phones, tablets, landscape, and split-screen`() {
        val smallPhone = computeAdaptiveMetrics(360.dp, 640.dp)
        assertEquals(WindowWidthClass.COMPACT, smallPhone.widthClass)
        assertEquals(WindowHeightClass.MEDIUM, smallPhone.heightClass)
        assertFalse(smallPhone.useNavigationRail)
        assertFalse(smallPhone.useTwoColumnLayout)
        assertFalse(smallPhone.useCompactLandscapeSplit)

        val largePhone = computeAdaptiveMetrics(412.dp, 915.dp)
        assertEquals(WindowWidthClass.COMPACT, largePhone.widthClass)
        assertEquals(WindowHeightClass.EXPANDED, largePhone.heightClass)
        assertFalse(largePhone.useNavigationRail)
        assertFalse(largePhone.useTwoColumnLayout)

        val phoneLandscape = computeAdaptiveMetrics(800.dp, 360.dp)
        assertEquals(WindowWidthClass.MEDIUM, phoneLandscape.widthClass)
        assertEquals(WindowHeightClass.COMPACT, phoneLandscape.heightClass)
        assertTrue(phoneLandscape.isLandscape)
        assertTrue(phoneLandscape.useNavigationRail)
        assertFalse(phoneLandscape.useTwoColumnLayout)
        assertTrue(phoneLandscape.useCompactLandscapeSplit)

        val tabletPortrait = computeAdaptiveMetrics(768.dp, 1024.dp)
        assertEquals(WindowWidthClass.MEDIUM, tabletPortrait.widthClass)
        assertEquals(WindowHeightClass.EXPANDED, tabletPortrait.heightClass)
        assertTrue(tabletPortrait.useNavigationRail)
        assertTrue(tabletPortrait.useTwoColumnLayout)

        val tabletLandscape = computeAdaptiveMetrics(1280.dp, 800.dp)
        assertEquals(WindowWidthClass.EXPANDED, tabletLandscape.widthClass)
        assertEquals(WindowHeightClass.MEDIUM, tabletLandscape.heightClass)
        assertTrue(tabletLandscape.useNavigationRail)
        assertTrue(tabletLandscape.useTwoColumnLayout)

        val narrowSplit = computeAdaptiveMetrics(500.dp, 700.dp)
        val wideSplit = computeAdaptiveMetrics(900.dp, 700.dp)
        assertFalse(narrowSplit.useNavigationRail)
        assertFalse(narrowSplit.useTwoColumnLayout)
        assertTrue(wideSplit.useNavigationRail)
        assertTrue(wideSplit.useTwoColumnLayout)
    }

    @Test
    fun `liquid glass navigation rail renders all four destinations and switches on tap`() {
        var current by mutableStateOf(BottomNavDestination.HOME)

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK) {
                ProvideAdaptiveMetrics(maxWidth = 1100.dp, maxHeight = 760.dp) {
                    BrainBufferLiquidGlassRail(
                        currentDestination = current,
                        onSelectDestination = { current = it },
                        isFocusTimerRunning = true,
                        hapticsEnabled = false
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag("brain_buffer_nav_rail").assertCountEquals(1)
        BottomNavDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag(destination.testTag).assertIsDisplayed()
        }

        listOf(
            BottomNavDestination.FOCUS,
            BottomNavDestination.INSIGHTS,
            BottomNavDestination.SETTINGS,
            BottomNavDestination.HOME
        ).forEach { target ->
            composeRule.onNodeWithTag(target.testTag).performClick()
            composeRule.waitForIdle()
            assertEquals(target, current)
        }
    }

    @Test
    fun `liquid glass navigation rail long press stationary does not navigate`() {
        var current by mutableStateOf(BottomNavDestination.HOME)
        var selectionCount = 0

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.LIGHT) {
                ProvideAdaptiveMetrics(maxWidth = 1100.dp, maxHeight = 760.dp) {
                    BrainBufferLiquidGlassRail(
                        currentDestination = current,
                        onSelectDestination = {
                            current = it
                            selectionCount += 1
                        },
                        hapticsEnabled = false
                    )
                }
            }
        }

        composeRule.onNodeWithTag(BottomNavDestination.INSIGHTS.testTag).performTouchInput {
            longClick()
        }
        composeRule.waitForIdle()

        assertEquals(BottomNavDestination.HOME, current)
        assertEquals(0, selectionCount)
    }

    @Test
    fun `liquid glass navigation rail vertical drag selects destination once and horizontal cancel stays safe`() {
        var current by mutableStateOf(BottomNavDestination.HOME)
        var selectionCount = 0

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK) {
                ProvideAdaptiveMetrics(maxWidth = 1100.dp, maxHeight = 760.dp) {
                    BrainBufferLiquidGlassRail(
                        currentDestination = current,
                        onSelectDestination = {
                            current = it
                            selectionCount += 1
                        },
                        hapticsEnabled = false
                    )
                }
            }
        }

        // Long press Home (top) and slide down to Insights (3rd slot of 4 -> ~62% height)
        composeRule.onNodeWithTag("brain_buffer_nav_rail").performTouchInput {
            down(Offset(width * 0.50f, height * 0.12f))
            advanceEventTime(320L)
            moveTo(Offset(width * 0.50f, height * 0.37f))
            advanceEventTime(60L)
            moveTo(Offset(width * 0.50f, height * 0.62f))
            advanceEventTime(60L)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(BottomNavDestination.INSIGHTS, current)
        assertEquals(1, selectionCount)

        // Drag far horizontally outside rail — must cancel without changing destination
        composeRule.onNodeWithTag("brain_buffer_nav_rail").performTouchInput {
            down(Offset(width * 0.50f, height * 0.62f))
            advanceEventTime(320L)
            moveTo(Offset(width * 0.50f, height * 0.12f))
            advanceEventTime(60L)
            moveTo(Offset(width * 0.50f + 500f, height * 0.12f))
            advanceEventTime(60L)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(BottomNavDestination.INSIGHTS, current)
        assertEquals(1, selectionCount)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h800dp")
    fun `dynamic resize swaps cleanly between bottom bar and navigation rail while preserving destination`() {
        var windowWidth by mutableStateOf(390.dp)
        var windowHeight by mutableStateOf(800.dp)
        var currentDest by mutableStateOf(BottomNavDestination.HOME)
        val uiState = BrainBufferUiState()

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK) {
                Box(modifier = Modifier.size(windowWidth, windowHeight)) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        ProvideAdaptiveMetrics(maxWidth = maxWidth, maxHeight = maxHeight) {
                            val adaptive = LocalAdaptiveMetrics.current
                            if (adaptive.useNavigationRail) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    BrainBufferLiquidGlassRail(
                                        currentDestination = currentDest,
                                        onSelectDestination = { currentDest = it },
                                        hapticsEnabled = false
                                    )
                                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                        when (currentDest) {
                                            BottomNavDestination.HOME -> HomeScreen(uiState = uiState)
                                            BottomNavDestination.FOCUS -> FocusScreen(uiState = uiState)
                                            BottomNavDestination.INSIGHTS -> InsightsPrimaryScreen(uiState = uiState)
                                            BottomNavDestination.SETTINGS -> ProfileScreen(uiState = uiState)
                                        }
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    HomeScreen(uiState = uiState)
                                    BrainBufferBottomBar(
                                        currentDestination = currentDest,
                                        onSelectDestination = { currentDest = it },
                                        hapticsEnabled = false,
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Phone portrait: strictly ONE bottom bar, ZERO navigation rails
        composeRule.onAllNodesWithTag("brain_buffer_bottom_bar").assertCountEquals(1)
        composeRule.onAllNodesWithTag("brain_buffer_nav_rail").assertCountEquals(0)

        // Resize dynamically to tablet landscape (1100 x 760 dp)
        windowWidth = 1100.dp
        windowHeight = 760.dp
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("brain_buffer_nav_rail").assertCountEquals(1)
        composeRule.onAllNodesWithTag("brain_buffer_bottom_bar").assertCountEquals(0)

        // Switch across all 4 destinations on tablet
        composeRule.onNodeWithTag(BottomNavDestination.FOCUS.testTag).performClick()
        composeRule.waitForIdle()
        assertEquals(BottomNavDestination.FOCUS, currentDest)
        composeRule.onNodeWithTag("focus_setup_headline").assertIsDisplayed()

        composeRule.onNodeWithTag(BottomNavDestination.INSIGHTS.testTag).performClick()
        composeRule.waitForIdle()
        assertEquals(BottomNavDestination.INSIGHTS, currentDest)
        composeRule.onNodeWithTag("insights_screen").assertIsDisplayed()

        composeRule.onNodeWithTag(BottomNavDestination.SETTINGS.testTag).performClick()
        composeRule.waitForIdle()
        assertEquals(BottomNavDestination.SETTINGS, currentDest)
        composeRule.onNodeWithTag("profile_list").assertIsDisplayed()

        // Resize back to narrow split-screen (480 x 760 dp) -> returns to bottom bar seamlessly
        windowWidth = 480.dp
        windowHeight = 760.dp
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("brain_buffer_bottom_bar").assertCountEquals(1)
        composeRule.onAllNodesWithTag("brain_buffer_nav_rail").assertCountEquals(0)
        assertEquals(BottomNavDestination.SETTINGS, currentDest)
    }
}
