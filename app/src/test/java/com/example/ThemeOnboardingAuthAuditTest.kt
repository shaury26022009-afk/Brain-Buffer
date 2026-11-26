package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.example.cloud.SupabaseConfig
import com.example.data.local.BrainBufferDatabase
import com.example.domain.DailyFocusGoalProgress
import com.example.model.SessionPurposeCategory
import com.example.model.SupabaseAuthSession
import com.example.model.UserProfileRole
import com.example.performance.AdaptiveBlurController
import com.example.performance.AdaptiveBlurPolicy
import com.example.performance.PerformanceProfile
import com.example.ui.theme.AmoledLiquidGlassColorScheme
import com.example.ui.theme.LightLiquidGlassColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure JVM audit verification tests for Theme Contrast (WCAG AA/AAA),
 * Room Database Migration contiguity (v1 -> v6), Supabase Auth invariants,
 * Onboarding bounds, and AdaptiveBlur performance policy.
 */
class ThemeOnboardingAuthAuditTest {

    private fun linearChannel(c: Float): Double =
        if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun relativeLuminance(color: Color): Double {
        return 0.2126 * linearChannel(color.red) +
            0.7152 * linearChannel(color.green) +
            0.0722 * linearChannel(color.blue)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val opaqueBg = if (background.alpha < 1f) background.compositeOver(Color.Black) else background
        val resolvedFg = if (foreground.alpha < 1f) foreground.compositeOver(opaqueBg) else foreground
        val l1 = relativeLuminance(resolvedFg)
        val l2 = relativeLuminance(opaqueBg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    @Test
    fun amoledDarkTheme_meetsWcagContrastAndTrueBlackCanvas() {
        val scheme = AmoledLiquidGlassColorScheme
        assertEquals(Color(0xFF000000), scheme.background)

        val surfaceResolved = scheme.surface.compositeOver(scheme.background)
        val primaryContainerResolved = scheme.primaryContainer.compositeOver(scheme.background)
        val errorContainerResolved = scheme.errorContainer.compositeOver(scheme.background)

        assertTrue("onBackground vs background >= 15:1", contrastRatio(scheme.onBackground, scheme.background) >= 15.0)
        assertTrue("onSurface vs surface >= 12:1", contrastRatio(scheme.onSurface, surfaceResolved) >= 12.0)
        assertTrue("onSurfaceVariant vs surface >= 7:1", contrastRatio(scheme.onSurfaceVariant, surfaceResolved) >= 7.0)
        assertTrue("onPrimary vs primary >= 7:1", contrastRatio(scheme.onPrimary, scheme.primary) >= 7.0)
        assertTrue("onPrimary vs dark button gradient end >= 6.5:1", contrastRatio(scheme.onPrimary, Color(0xFFB39DF9)) >= 6.5)
        assertTrue("onPrimaryContainer vs primaryContainer >= 7:1", contrastRatio(scheme.onPrimaryContainer, primaryContainerResolved) >= 7.0)
        assertTrue("onSecondary vs secondary >= 7:1", contrastRatio(scheme.onSecondary, scheme.secondary) >= 7.0)
        assertTrue("onError vs error >= 5:1", contrastRatio(scheme.onError, scheme.error) >= 5.0)
        assertTrue("onErrorContainer vs errorContainer >= 7:1", contrastRatio(scheme.onErrorContainer, errorContainerResolved) >= 7.0)
    }

    @Test
    fun lightTheme_meetsWcagContrastAndCrispOutlineAlpha() {
        val scheme = LightLiquidGlassColorScheme
        val surfaceResolved = scheme.surface.compositeOver(scheme.background)
        val primaryContainerResolved = scheme.primaryContainer.compositeOver(scheme.background)
        val errorContainerResolved = scheme.errorContainer.compositeOver(scheme.background)

        assertTrue("Light onBackground vs background >= 12:1", contrastRatio(scheme.onBackground, scheme.background) >= 12.0)
        assertTrue("Light onSurface vs surface >= 12:1", contrastRatio(scheme.onSurface, surfaceResolved) >= 12.0)
        assertTrue("Light onSurfaceVariant vs surface >= 5.5:1", contrastRatio(scheme.onSurfaceVariant, surfaceResolved) >= 5.5)
        assertTrue("Light onPrimary vs primary >= 4.5:1", contrastRatio(scheme.onPrimary, scheme.primary) >= 4.5)
        assertTrue("Light onPrimaryContainer vs primaryContainer >= 7:1", contrastRatio(scheme.onPrimaryContainer, primaryContainerResolved) >= 7.0)
        assertTrue("Light onError vs error >= 4.5:1", contrastRatio(scheme.onError, scheme.error) >= 4.5)
        assertTrue("Light onErrorContainer vs errorContainer >= 7:1", contrastRatio(scheme.onErrorContainer, errorContainerResolved) >= 7.0)
        assertTrue("Light outline alpha >= 0.25 for crisp borders", scheme.outline.alpha >= 0.25f)
    }

    @Test
    fun roomDatabaseMigrations_areContiguousFromV1ToV6() {
        val migrations = listOf(
            BrainBufferDatabase.MIGRATION_1_2,
            BrainBufferDatabase.MIGRATION_2_3,
            BrainBufferDatabase.MIGRATION_3_4,
            BrainBufferDatabase.MIGRATION_4_5,
            BrainBufferDatabase.MIGRATION_5_6
        )
        assertEquals(5, migrations.size)
        for (idx in migrations.indices) {
            assertEquals("Expected startVersion ${idx + 1}", idx + 1, migrations[idx].startVersion)
            assertEquals("Expected endVersion ${idx + 2}", idx + 2, migrations[idx].endVersion)
        }
    }

    @Test
    fun supabaseAuthSession_andConfigInvariants_holdWithoutGoogleOAuth() {
        val guest = SupabaseAuthSession()
        assertFalse(guest.isSignedIn)

        val nowSec = System.currentTimeMillis() / 1000L
        val active = SupabaseAuthSession(
            userId = "user-123",
            email = "student@example.com",
            accessToken = "jwt-token",
            refreshToken = "refresh-token",
            expiresAtEpochSec = nowSec + 3600L,
            providers = listOf("email")
        )
        assertTrue(active.isSignedIn)
        assertFalse(active.isExpired(nowSec))

        val expired = active.copy(expiresAtEpochSec = nowSec - 10L)
        assertTrue(expired.isExpired(nowSec))

        assertEquals("com.aistudio.brainbuffer.vkrqmx://auth-callback", SupabaseConfig.AUTH_REDIRECT_URI)
    }

    @Test
    fun onboardingRolesPurposesAndGoalBounds_areConsistent() {
        assertTrue(UserProfileRole.entries.contains(UserProfileRole.STUDENT))
        assertTrue(UserProfileRole.entries.contains(UserProfileRole.PROFESSIONAL))
        assertTrue(UserProfileRole.entries.contains(UserProfileRole.PERSONAL))
        assertTrue(SessionPurposeCategory.entries.size >= 4)
        assertEquals(15, DailyFocusGoalProgress.MIN_GOAL_MINUTES)
        assertEquals(480, DailyFocusGoalProgress.MAX_GOAL_MINUTES)
    }

    @Test
    fun adaptiveBlurController_downgradesCleanlyWithinBoundedFrameWindow() {
        val controller = AdaptiveBlurController(
            initialProfile = PerformanceProfile.PREMIUM,
            initialSdkInt = 34,
            initialPowerSaveMode = false
        )
        val initialScale = controller.state.value.dynamicScale
        repeat(AdaptiveBlurPolicy.WINDOW_FRAME_COUNT) {
            controller.recordFrameDurationMs(26f) // Slow frames trigger step-down
        }
        assertTrue(
            "Expected dynamicScale to step down after slow burst window",
            controller.state.value.dynamicScale < initialScale
        )
    }
}
