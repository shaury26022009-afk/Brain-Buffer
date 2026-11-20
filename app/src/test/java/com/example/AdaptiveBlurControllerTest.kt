package com.example

import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.model.ThemeMode
import com.example.performance.AdaptiveBlurController
import com.example.performance.AdaptiveBlurPolicy
import com.example.performance.AdaptiveBlurState
import com.example.performance.BlurPerformanceTier
import com.example.performance.BlurSurfaceRole
import com.example.performance.PerformanceProfile
import com.example.performance.PerformanceProfileOverride
import com.example.ui.components.BottomNavDestination
import com.example.ui.components.BrainBufferBottomBar
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.BrainBufferTheme
import com.example.ui.theme.BrainBufferThemeTokens
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdaptiveBlurControllerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `blur radii scale deterministically across High, Medium, Low tiers and Android 10-11 fallback`() {
        val high = AdaptiveBlurState(
            tier = BlurPerformanceTier.HIGH,
            profile = PerformanceProfile.PREMIUM,
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        )
        assertEquals(14.dp, high.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))
        assertEquals(10.dp, high.radiusFor(BlurSurfaceRole.FLOATING_PILL))
        assertEquals(8.dp, high.radiusFor(BlurSurfaceRole.MODAL_SHEET_OR_DIALOG))
        assertEquals(6.dp, high.radiusFor(BlurSurfaceRole.HERO_CARD))
        // Scrolling cards must NEVER blur regardless of tier
        assertEquals(0.dp, high.radiusFor(BlurSurfaceRole.SCROLLING_CARD))
        assertEquals(0f, high.alphaCompensationFor(BlurSurfaceRole.NAVIGATION_TRACK), 0.001f)

        val medium = AdaptiveBlurState(
            tier = BlurPerformanceTier.MEDIUM,
            profile = PerformanceProfile.BALANCED,
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        )
        assertEquals(8.dp, medium.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))
        assertEquals(6.dp, medium.radiusFor(BlurSurfaceRole.FLOATING_PILL))
        assertEquals(4.dp, medium.radiusFor(BlurSurfaceRole.MODAL_SHEET_OR_DIALOG))
        assertEquals(0.dp, medium.radiusFor(BlurSurfaceRole.HERO_CARD))
        assertEquals(0.dp, medium.radiusFor(BlurSurfaceRole.SCROLLING_CARD))

        val low = AdaptiveBlurState(
            tier = BlurPerformanceTier.LOW,
            profile = PerformanceProfile.PERFORMANCE,
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        )
        BlurSurfaceRole.entries.forEach { role ->
            assertEquals(0.dp, low.radiusFor(role))
            assertFalse(low.isBlurActiveFor(role))
        }
        assertTrue(low.alphaCompensationFor(BlurSurfaceRole.NAVIGATION_TRACK) > 0f)

        // Android 10 (API 29) has no RenderEffect -> always 0.dp with opacity compensation
        val android10 = high.copy(sdkInt = Build.VERSION_CODES.Q)
        assertFalse(android10.supportsHardwareBlur)
        assertEquals(0.dp, android10.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))
        assertTrue(android10.alphaCompensationFor(BlurSurfaceRole.NAVIGATION_TRACK) > 0f)
    }

    @Test
    fun `60fps frame-budget watchdog steps blur down under jank, ignores idle pauses, and recovers with hysteresis`() {
        val controller = AdaptiveBlurController(
            initialProfile = PerformanceProfile.PREMIUM,
            initialSdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        )
        assertEquals(14.dp, controller.state.value.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))
        assertFalse(controller.state.value.isFrameBudgetGuarded)

        // Idle gaps (> 120ms) on a static screen must NOT trigger false jank downgrade
        repeat(40) { controller.recordFrameDurationMs(250f) }
        assertEquals(1.0f, controller.state.value.dynamicScale, 0.001f)
        assertEquals(14.dp, controller.state.value.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))

        // Window 1 of janky 26ms frames (~38fps) -> step down 1.0f -> 0.72f (10.0dp)
        repeat(AdaptiveBlurPolicy.WINDOW_FRAME_COUNT) { controller.recordFrameDurationMs(26f) }
        assertEquals(0.72f, controller.state.value.dynamicScale, 0.001f)
        assertTrue(controller.state.value.isFrameBudgetGuarded)
        assertEquals(10.dp, controller.state.value.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))

        // Window 2 of janky frames -> step down 0.72f -> 0.45f (6.5dp)
        repeat(AdaptiveBlurPolicy.WINDOW_FRAME_COUNT) { controller.recordFrameDurationMs(26f) }
        assertEquals(0.45f, controller.state.value.dynamicScale, 0.001f)
        assertEquals(6.5.dp, controller.state.value.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))

        // Window 3 of janky frames -> step down 0.45f -> 0.0f (0.dp — full zero-blur protection)
        repeat(AdaptiveBlurPolicy.WINDOW_FRAME_COUNT) { controller.recordFrameDurationMs(26f) }
        assertEquals(0.0f, controller.state.value.dynamicScale, 0.001f)
        assertEquals(0.dp, controller.state.value.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))

        // 1 smooth window is NOT enough to bounce back immediately (hysteresis prevents flicker)
        repeat(AdaptiveBlurPolicy.WINDOW_FRAME_COUNT) { controller.recordFrameDurationMs(15.5f) }
        assertEquals(0.0f, controller.state.value.dynamicScale, 0.001f)

        // Sustained smooth 60fps windows eventually step back up smoothly
        repeat(AdaptiveBlurPolicy.WINDOW_FRAME_COUNT * 4) { controller.recordFrameDurationMs(15.5f) }
        assertTrue(controller.state.value.dynamicScale > 0.0f)
        assertTrue(controller.state.value.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK) > 0.dp)
    }

    @Test
    fun `thermal throttling, battery saver, and mid-tier gesture motion guard blur smoothly`() {
        val controller = AdaptiveBlurController(
            initialProfile = PerformanceProfile.BALANCED,
            initialSdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        )
        assertEquals(8.dp, controller.state.value.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))

        // Active gesture motion on Medium tier gently trims blur (8dp * 0.85 = 6.8 -> 7.0dp)
        controller.setInteractiveMotion(true)
        assertEquals(7.dp, controller.state.value.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))
        controller.setInteractiveMotion(false)
        assertEquals(8.dp, controller.state.value.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))

        // Moderate thermal pressure trims blur by 0.65x (8dp * 0.65 = 5.2 -> 5.0dp)
        controller.updateEnvironment(
            profile = PerformanceProfile.BALANCED,
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            isPowerSaveMode = false,
            thermalStatus = PowerManager.THERMAL_STATUS_MODERATE
        )
        assertTrue(controller.state.value.isThermalThrottled)
        assertEquals(5.dp, controller.state.value.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))

        // Severe thermal pressure pauses blur completely
        controller.updateEnvironment(
            profile = PerformanceProfile.BALANCED,
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            isPowerSaveMode = false,
            thermalStatus = PowerManager.THERMAL_STATUS_SEVERE
        )
        assertEquals(0.dp, controller.state.value.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))

        // Battery Saver pauses blur completely even at High tier
        controller.updateEnvironment(
            profile = PerformanceProfile.PREMIUM,
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            isPowerSaveMode = true,
            thermalStatus = PowerManager.THERMAL_STATUS_NONE
        )
        assertEquals(0.dp, controller.state.value.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))
    }

    @Test
    fun `BrainBufferTheme wires AdaptiveBlurController across High, Medium, Low overrides and Settings UI`() {
        var override by mutableStateOf(PerformanceProfileOverride.PREMIUM)
        var observedState: AdaptiveBlurState? = null

        composeRule.setContent {
            BrainBufferTheme(
                themeMode = ThemeMode.DARK,
                performanceOverride = override
            ) {
                observedState = BrainBufferThemeTokens.blurState
                ProvideAdaptiveMetrics(maxWidth = 412.dp, maxHeight = 915.dp) {
                    BrainBufferBottomBar(
                        currentDestination = BottomNavDestination.HOME,
                        onSelectDestination = {},
                        hapticsEnabled = false
                    )
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals(BlurPerformanceTier.HIGH, observedState?.tier)
        assertEquals(14.dp, observedState?.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))

        override = PerformanceProfileOverride.BALANCED
        composeRule.waitForIdle()
        assertEquals(BlurPerformanceTier.MEDIUM, observedState?.tier)
        assertEquals(8.dp, observedState?.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))

        override = PerformanceProfileOverride.PERFORMANCE
        composeRule.waitForIdle()
        assertEquals(BlurPerformanceTier.LOW, observedState?.tier)
        assertEquals(0.dp, observedState?.radiusFor(BlurSurfaceRole.NAVIGATION_TRACK))
    }

    @Test
    fun `Profile settings displays live Adaptive Blur status badge`() {
        composeRule.setContent {
            BrainBufferTheme(
                themeMode = ThemeMode.DARK,
                performanceOverride = PerformanceProfileOverride.PREMIUM
            ) {
                ProvideAdaptiveMetrics(maxWidth = 412.dp, maxHeight = 915.dp) {
                    ProfileScreen(
                        uiState = BrainBufferUiState(),
                        onUpdateTheme = {},
                        onUpdateProfile = { _, _, _, _, _, _, _, _, _, _ -> },
                        onSaveSubject = {},
                        onDeleteSubject = {},
                        onUpdateTimerAndTarget = { _, _, _, _ -> },
                        onUpdateAppSettings = { _, _, _, _, _ -> },
                        onOpenMindReset = {},
                        onLoadDemoData = {},
                        onClearAllData = {},
                        onResetOnboarding = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("profile_tab_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_adaptive_blur_status").performScrollTo().assertIsDisplayed()
    }
}
