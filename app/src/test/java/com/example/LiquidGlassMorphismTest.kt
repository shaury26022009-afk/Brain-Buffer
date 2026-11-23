package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.model.ActivityAllowanceConfig
import com.example.model.FocusGuardSettings
import com.example.model.ThemeMode
import com.example.performance.PerformanceProfile
import com.example.performance.PerformanceProfileOverride
import com.example.performance.RenderQualityTokens
import com.example.ui.components.ControlledPhoneAccessContent
import com.example.ui.components.FocusGuardSettingsContent
import com.example.ui.components.LiquidGlassSegmentedControl
import com.example.ui.components.LiquidGlassTokens
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.GardenScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlannerScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.StrictFocusScreen
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.AmoledSecondaryBg
import com.example.ui.theme.BrainBufferTheme
import com.example.ui.theme.LightBackground
import com.example.ui.theme.ProvideAdaptiveMetrics
import com.example.viewmodel.BrainBufferUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the unified BRAIN BUFFER Liquid Glass Morphism design system:
 * - Pure AMOLED Black (#000000 / #090909) & Frosted Light (#FAF9FD) tokens
 * - Adaptive performance profile scaling (Performance / Balanced / Premium) & reduced motion
 * - LiquidGlassSegmentedControl state transitions
 * - End-to-end rendering of all upgraded Liquid Glass screens & sheets across phone & tablet
 * - Interactive Theme & Performance selectors on Profile -> Settings
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiquidGlassMorphismTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `liquid glass tokens enforce pure AMOLED black and adapt across performance profiles and reduced motion`() {
        assertEquals(Color(0xFF000000), AmoledBlack)
        assertEquals(Color(0xFF090909), AmoledSecondaryBg)
        assertEquals(Color(0xFFFAF9FD), LightBackground)

        val perfQuality = RenderQualityTokens.forProfile(PerformanceProfile.PERFORMANCE, reducedMotion = false)
        val balancedQuality = RenderQualityTokens.forProfile(PerformanceProfile.BALANCED, reducedMotion = false)
        val premiumQuality = RenderQualityTokens.forProfile(PerformanceProfile.PREMIUM, reducedMotion = false)

        val darkPerf = LiquidGlassTokens.resolve(isDark = true, renderQuality = perfQuality, reducedMotion = false)
        val darkBalanced = LiquidGlassTokens.resolve(isDark = true, renderQuality = balancedQuality, reducedMotion = false)
        val darkPremium = LiquidGlassTokens.resolve(isDark = true, renderQuality = premiumQuality, reducedMotion = false)
        val lightPremium = LiquidGlassTokens.resolve(isDark = false, renderQuality = premiumQuality, reducedMotion = false)
        val reducedTokens = LiquidGlassTokens.resolve(isDark = true, renderQuality = premiumQuality, reducedMotion = true)

        assertTrue(darkPremium.isDark)
        assertFalse(lightPremium.isDark)
        assertNotEquals(darkPremium.surfaceTop, lightPremium.surfaceTop)
        assertNotEquals(darkPremium.sheetBackground, lightPremium.sheetBackground)

        // Performance profile disables blur and ambient shadows for budget Android 10+ smoothness
        assertFalse(darkPerf.enableBlur)
        assertFalse(darkPerf.enableAmbientShadows)
        assertEquals(0f, darkPerf.blurRadiusDp, 0.001f)

        // Balanced and Premium progressively enable lightweight selective glass depth
        assertTrue(darkBalanced.enableSpecularSheen)
        assertTrue(darkPremium.enableAmbientShadows)
        assertTrue(darkPremium.blurRadiusDp >= darkBalanced.blurRadiusDp)

        // Reduced motion disables spring bounce (critical damping = 1.0f)
        assertEquals(1.0f, reducedTokens.springDamping, 0.001f)
        assertTrue(darkPremium.springDamping < 1.0f)
    }

    @Test
    fun `liquid glass segmented control switches smoothly across all segments in Light and AMOLED Dark`() {
        val segments = listOf("Focus", "Short Break", "Long Break")
        var selected by mutableStateOf(segments[0])
        var themeMode by mutableStateOf(ThemeMode.DARK)

        composeRule.setContent {
            BrainBufferTheme(themeMode = themeMode, performanceOverride = PerformanceProfileOverride.PREMIUM) {
                Box(modifier = Modifier.padding(16.dp)) {
                    LiquidGlassSegmentedControl(
                        items = segments,
                        selectedItem = selected,
                        onSelect = { selected = it },
                        labelProvider = { it },
                        testTagProvider = { "seg_${it.lowercase().replace(' ', '_')}" }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("seg_short_break").performClick()
        composeRule.waitForIdle()
        assertEquals("Short Break", selected)

        themeMode = ThemeMode.LIGHT
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("seg_long_break").performClick()
        composeRule.waitForIdle()
        assertEquals("Long Break", selected)

        composeRule.onNodeWithTag("seg_focus").performClick()
        composeRule.waitForIdle()
        assertEquals("Focus", selected)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h800dp")
    fun `all core Liquid Glass screens and sheets render cleanly in Light and AMOLED Dark on phone and tablet`() {
        var screenIndex by mutableStateOf(0)
        var themeMode by mutableStateOf(ThemeMode.DARK)
        var windowW by mutableStateOf(412.dp)
        var windowH by mutableStateOf(800.dp)
        val uiState = BrainBufferUiState()

        composeRule.setContent {
            BrainBufferTheme(themeMode = themeMode, performanceOverride = PerformanceProfileOverride.PREMIUM) {
                Box(modifier = Modifier.size(windowW, windowH)) {
                    ProvideAdaptiveMetrics(maxWidth = windowW, maxHeight = windowH) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (screenIndex) {
                                0 -> HomeScreen(uiState = uiState)
                                1 -> FocusScreen(uiState = uiState)
                                2 -> PlannerScreen(
                                    uiState = uiState,
                                    onToggleTask = {},
                                    onSaveTask = { _, _, _, _, _, _, _, _ -> },
                                    onDeleteTask = {},
                                    onUndoDelete = {}
                                )
                                3 -> GardenScreen(
                                    uiState = uiState,
                                    onSelectActivePlant = {},
                                    onNurtureWithSeed = { false },
                                    onPlantNewSeed = { _, _ -> false },
                                    onStartFocus = {}
                                )
                                4 -> ProfileScreen(uiState = uiState)
                                5 -> StrictFocusScreen(
                                    uiState = uiState,
                                    onBack = {},
                                    onStartStrictFocus = { _, _, _, _, _, _, _ -> },
                                    onPause = {},
                                    onResume = {},
                                    onExtendFiveMin = {},
                                    onToggleAodStyle = {},
                                    onOpenActivitySwitcher = {},
                                    onExitStrictFocus = {},
                                    onSavePreset = {},
                                    onDeletePreset = {}
                                )
                                6 -> ControlledPhoneAccessContent(
                                    allowances = ActivityAllowanceConfig(),
                                    appCategoryMappings = emptyList(),
                                    todayEntertainmentMinutes = 0,
                                    hasActiveStudySession = true,
                                    hapticsEnabled = false,
                                    onDismiss = {},
                                    onApproveActivity = { _, _, _, _, _, _, _ -> }
                                )
                                7 -> FocusGuardSettingsContent(
                                    settings = FocusGuardSettings(),
                                    appUsageAwarenessEnabled = false,
                                    appCategoryMappings = emptyList(),
                                    onUpdateSettings = {},
                                    onUpdateAppUsageAwareness = {},
                                    onUpsertAppMapping = {},
                                    onDeleteAppMapping = {},
                                    onSaveStarterPreset = {},
                                    onDismiss = {}
                                )
                            }
                        }
                    }
                }
            }
        }

        // Phone AMOLED Dark checks
        composeRule.onNodeWithTag("home_today_alignment_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_start_focus_button").assertIsDisplayed()

        screenIndex = 1
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("focus_setup_headline").assertIsDisplayed()

        screenIndex = 2
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("planner_tab_today").assertIsDisplayed()
        composeRule.onNodeWithTag("planner_tab_weekly").assertIsDisplayed()

        screenIndex = 3
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("living_garden_canvas").assertIsDisplayed()

        screenIndex = 4
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_list").assertIsDisplayed()

        screenIndex = 5
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("strict_focus_setup_screen").assertIsDisplayed()

        screenIndex = 6
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("controlled_access_option_pdfs_and_drive").assertExists()

        screenIndex = 7
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("focus_guard_settings_sheet").assertIsDisplayed()

        // Tablet Landscape Light theme checks
        themeMode = ThemeMode.LIGHT
        windowW = 1200.dp
        windowH = 800.dp

        screenIndex = 0
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()

        screenIndex = 2
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("planner_tab_weekly").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `settings Liquid Glass theme selector renders and displays in Light mode`() {
        composeRule.setContent {
            BrainBufferTheme(themeMode = ThemeMode.LIGHT, performanceOverride = PerformanceProfileOverride.AUTO) {
                ProvideAdaptiveMetrics(maxWidth = 412.dp, maxHeight = 915.dp) {
                    ProfileScreen(uiState = BrainBufferUiState())
                }
            }
        }

        composeRule.onNodeWithTag("profile_list").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_theme_selector").assertIsDisplayed()
    }
}
