package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window width breakpoints aligned with Android Material 3 Window Size Classes:
 * - COMPACT: < 600.dp (portrait phones, narrow split-screen windows)
 * - MEDIUM: 600.dp..839.dp (landscape phones, small tablets, unfolded foldables)
 * - EXPANDED: >= 840.dp (tablets in landscape/portrait, desktop/ChromeOS windows)
 */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

/**
 * Window height breakpoints aligned with Android Material 3 Window Size Classes:
 * - COMPACT: < 480.dp (phones in landscape, short split-screen panes)
 * - MEDIUM: 480.dp..899.dp (portrait phones, landscape tablets)
 * - EXPANDED: >= 900.dp (tall tablets in portrait)
 */
enum class WindowHeightClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

@Immutable
data class BrainBufferAdaptiveMetrics(
    val windowWidth: Dp = 412.dp,
    val windowHeight: Dp = 915.dp,
    val widthClass: WindowWidthClass = WindowWidthClass.COMPACT,
    val heightClass: WindowHeightClass = WindowHeightClass.MEDIUM
) {
    val isLandscape: Boolean
        get() = windowWidth > windowHeight

    val isCompactHeight: Boolean
        get() = heightClass == WindowHeightClass.COMPACT

    /**
     * Switches cleanly between the floating bottom Liquid Glass pill (portrait phones & narrow
     * split-screen) and the floating left Liquid Glass rail (tablets & landscape phones/windows).
     */
    val useNavigationRail: Boolean
        get() = when (widthClass) {
            WindowWidthClass.EXPANDED -> true
            WindowWidthClass.MEDIUM -> isLandscape || heightClass == WindowHeightClass.COMPACT || windowWidth >= 700.dp
            WindowWidthClass.COMPACT -> false
        }

    /**
     * Enables balanced two-column tablet layouts when horizontal space comfortably supports two panes.
     */
    val useTwoColumnLayout: Boolean
        get() = widthClass == WindowWidthClass.EXPANDED ||
            (widthClass == WindowWidthClass.MEDIUM && windowWidth >= 680.dp && !isCompactHeight)

    /**
     * Enables compact side-by-side layouts (such as Focus timer + controls) in phone landscape.
     */
    val useCompactLandscapeSplit: Boolean
        get() = isCompactHeight && windowWidth >= 560.dp

    val horizontalPadding: Dp
        get() = when (widthClass) {
            WindowWidthClass.COMPACT -> 20.dp
            WindowWidthClass.MEDIUM -> 26.dp
            WindowWidthClass.EXPANDED -> 32.dp
        }

    val sectionSpacing: Dp
        get() = when (widthClass) {
            WindowWidthClass.COMPACT -> 16.dp
            WindowWidthClass.MEDIUM -> 18.dp
            WindowWidthClass.EXPANDED -> 20.dp
        }

    val maxContentWidth: Dp
        get() = when {
            useTwoColumnLayout -> 1140.dp
            widthClass == WindowWidthClass.MEDIUM -> 780.dp
            else -> 640.dp
        }

    /**
     * Bottom clearance for scrollable lists so content stays clear of the floating bottom pill
     * on phones while remaining compact when the left navigation rail is active.
     */
    val bottomContentClearance: Dp
        get() = if (useNavigationRail) 32.dp else 136.dp

    /**
     * Bottom offset for floating action buttons & snackbars above the navigation bar.
     */
    val fabBottomPadding: Dp
        get() = if (useNavigationRail) 24.dp else 116.dp
}

fun computeAdaptiveMetrics(
    maxWidth: Dp,
    maxHeight: Dp
): BrainBufferAdaptiveMetrics {
    val widthClass = when {
        maxWidth < 600.dp -> WindowWidthClass.COMPACT
        maxWidth < 840.dp -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.EXPANDED
    }
    val heightClass = when {
        maxHeight < 480.dp -> WindowHeightClass.COMPACT
        maxHeight < 900.dp -> WindowHeightClass.MEDIUM
        else -> WindowHeightClass.EXPANDED
    }
    return BrainBufferAdaptiveMetrics(
        windowWidth = maxWidth,
        windowHeight = maxHeight,
        widthClass = widthClass,
        heightClass = heightClass
    )
}

val LocalAdaptiveMetrics = compositionLocalOf { BrainBufferAdaptiveMetrics() }

@Composable
fun ProvideAdaptiveMetrics(
    maxWidth: Dp,
    maxHeight: Dp,
    content: @Composable () -> Unit
) {
    val metrics = remember(maxWidth, maxHeight) {
        computeAdaptiveMetrics(maxWidth = maxWidth, maxHeight = maxHeight)
    }
    CompositionLocalProvider(
        LocalAdaptiveMetrics provides metrics,
        content = content
    )
}
