package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.AppRoutes
import com.example.ui.components.BottomNavDestination

/**
 * Encapsulates Compose Navigation state management for Brain Buffer, including
 * top-level tab switching with state save/restore, sub-screen routing, and
 * bottom navigation visibility rules.
 */
@Stable
class BrainBufferNavigationState(
    val navController: NavHostController
) {
    val currentDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    val currentRoute: String?
        @Composable get() = currentDestination?.route

    val currentTopLevelDestination: BottomNavDestination?
        @Composable get() = BottomNavDestination.entries.firstOrNull { it.route == currentRoute }

    @Composable
    fun shouldShowMainNavigation(isZenMode: Boolean): Boolean {
        return currentTopLevelDestination != null && !isZenMode
    }

    fun navigateToTopLevel(destination: BottomNavDestination) {
        navigateToTabRoute(destination.route)
    }

    fun navigateToTabRoute(route: String) {
        if (navController.currentDestination?.route == route) return
        navController.navigate(route) {
            popUpTo(AppRoutes.HOME) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateAfterSplash() {
        navController.navigate(AppRoutes.ONBOARDING) {
            popUpTo(AppRoutes.SPLASH) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun navigateFromSplashToHome() {
        navController.navigate(AppRoutes.HOME) {
            popUpTo(AppRoutes.SPLASH) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun navigateAfterOnboarding() {
        navController.navigate(AppRoutes.HOME) {
            popUpTo(AppRoutes.ONBOARDING) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun navigateToBlock() {
        navigateToTopLevel(BottomNavDestination.FOCUS)
    }

    fun navigateToInsights() {
        navigateToTopLevel(BottomNavDestination.INSIGHTS)
    }

    fun navigateToMindReset() {
        navController.navigate(AppRoutes.MIND_RESET) {
            launchSingleTop = true
        }
    }

    fun navigateToProfile() {
        navigateToTopLevel(BottomNavDestination.SETTINGS)
    }

    fun navigateToSettings() {
        navigateToTopLevel(BottomNavDestination.SETTINGS)
    }

    fun navigateToStrictFocus() {
        navController.navigate(AppRoutes.STRICT_FOCUS) {
            launchSingleTop = true
        }
    }

    fun navigateToAuth() {
        navController.navigate(AppRoutes.AUTH) {
            launchSingleTop = true
        }
    }

    fun replayOnboarding() {
        navController.navigate(AppRoutes.ONBOARDING) {
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    fun navigateBack() {
        navController.popBackStack()
    }
}

@Composable
fun rememberBrainBufferNavigationState(
    navController: NavHostController = rememberNavController()
): BrainBufferNavigationState {
    return remember(navController) {
        BrainBufferNavigationState(navController)
    }
}
