package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.model.ThemeMode
import com.example.ui.components.BottomNavDestination
import com.example.ui.components.BrainBufferBottomBar
import com.example.ui.theme.BrainBufferTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiquidGlassNavBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `single liquid glass bar renders all four destinations and switches smoothly on repeated taps`() {
        var current by mutableStateOf(BottomNavDestination.HOME)

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK) {
                BrainBufferBottomBar(
                    currentDestination = current,
                    onSelectDestination = { current = it },
                    isFocusTimerRunning = true,
                    hapticsEnabled = false
                )
            }
        }

        // Exactly one floating Liquid Glass navigation bar with 4 destinations exists
        composeRule.onAllNodesWithTag("brain_buffer_bottom_bar").assertCountEquals(1)
        assertEquals(4, BottomNavDestination.entries.size)

        BottomNavDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag(destination.testTag).assertIsDisplayed()
        }

        // Repeatedly switch across all four destinations
        listOf(
            BottomNavDestination.FOCUS,
            BottomNavDestination.INSIGHTS,
            BottomNavDestination.SETTINGS,
            BottomNavDestination.HOME,
            BottomNavDestination.INSIGHTS,
            BottomNavDestination.FOCUS
        ).forEach { target ->
            composeRule.onNodeWithTag(target.testTag).performClick()
            composeRule.waitForIdle()
            assertEquals(target, current)
        }
    }

    @Test
    fun `stationary long press expands lens without accidental navigation`() {
        var current by mutableStateOf(BottomNavDestination.HOME)
        var selectionCount = 0

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.LIGHT) {
                BrainBufferBottomBar(
                    currentDestination = current,
                    onSelectDestination = {
                        current = it
                        selectionCount += 1
                    },
                    hapticsEnabled = false
                )
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
    fun `long press then horizontal drag follows finger and opens destination once on release`() {
        var current by mutableStateOf(BottomNavDestination.HOME)
        var selectionCount = 0

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK) {
                BrainBufferBottomBar(
                    currentDestination = current,
                    onSelectDestination = {
                        current = it
                        selectionCount += 1
                    },
                    hapticsEnabled = false
                )
            }
        }

        composeRule.onNodeWithTag("brain_buffer_bottom_bar").performTouchInput {
            val start = Offset(width * 0.12f, height * 0.50f)
            val end = Offset(width * 0.62f, height * 0.50f) // Insights (slot 3 of 4)
            down(start)
            advanceEventTime(320L)
            moveTo(Offset(width * 0.37f, height * 0.50f))
            advanceEventTime(60L)
            moveTo(end)
            advanceEventTime(60L)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(BottomNavDestination.INSIGHTS, current)
        assertEquals(1, selectionCount)
    }

    @Test
    fun `horizontal drag across bar tracks smoothly and navigates on release`() {
        var current by mutableStateOf(BottomNavDestination.HOME)

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.LIGHT) {
                BrainBufferBottomBar(
                    currentDestination = current,
                    onSelectDestination = { current = it },
                    hapticsEnabled = false
                )
            }
        }

        // Drag from Home (left) across to Settings (right)
        composeRule.onNodeWithTag("brain_buffer_bottom_bar").performTouchInput {
            swipe(
                start = Offset(width * 0.12f, height * 0.50f),
                end = Offset(width * 0.90f, height * 0.50f),
                durationMillis = 300L
            )
        }
        composeRule.waitForIdle()

        assertEquals(BottomNavDestination.SETTINGS, current)
    }

    @Test
    fun `dragging far outside bar vertically cancels safely without navigation`() {
        var current by mutableStateOf(BottomNavDestination.HOME)
        var selectionCount = 0

        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.DARK) {
                BrainBufferBottomBar(
                    currentDestination = current,
                    onSelectDestination = {
                        current = it
                        selectionCount += 1
                    },
                    hapticsEnabled = false
                )
            }
        }

        composeRule.onNodeWithTag("brain_buffer_bottom_bar").performTouchInput {
            down(Offset(width * 0.12f, height * 0.50f))
            advanceEventTime(320L)
            moveTo(Offset(width * 0.62f, height * 0.50f))
            advanceEventTime(60L)
            moveTo(Offset(width * 0.62f, -600f))
            advanceEventTime(60L)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(BottomNavDestination.HOME, current)
        assertEquals(0, selectionCount)
    }
}
