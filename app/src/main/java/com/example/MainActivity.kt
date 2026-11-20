package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ThemeMode
import com.example.notifications.BrainBufferNotificationManager
import com.example.ui.BrainBufferApp
import com.example.ui.components.AppRoutes
import com.example.ui.components.BottomNavDestination
import com.example.ui.navigation.rememberBrainBufferNavigationState
import com.example.ui.theme.BrainBufferTheme
import com.example.viewmodel.BrainBufferViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BrainBufferViewModel by viewModels()
    private val isAppInForegroundState = mutableStateOf(true)
    private val pendingNotificationRoute = mutableStateOf<String?>(null)
    private val pendingOpenControlledAccess = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BrainBufferNotificationManager.ensureChannels(applicationContext)
        pendingNotificationRoute.value = intent?.getStringExtra(BrainBufferNotificationManager.EXTRA_OPEN_ROUTE)
        pendingOpenControlledAccess.value = intent?.getBooleanExtra(
            BrainBufferNotificationManager.EXTRA_OPEN_CONTROLLED_ACCESS,
            false
        ) ?: false

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val navigationState = rememberBrainBufferNavigationState()
            val systemDark = isSystemInDarkTheme()
            val useDark = when (uiState.preferences.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            val isInForeground by isAppInForegroundState
            val deepLinkRoute by pendingNotificationRoute
            val openControlledAccess by pendingOpenControlledAccess

            LaunchedEffect(deepLinkRoute, uiState.preferences.isLoaded, uiState.preferences.onboardingCompleted) {
                if (deepLinkRoute != null && uiState.preferences.isLoaded && uiState.preferences.onboardingCompleted) {
                    when (deepLinkRoute) {
                        AppRoutes.STRICT_FOCUS -> navigationState.navigateToStrictFocus()
                        AppRoutes.FOCUS -> navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
                    }
                    pendingNotificationRoute.value = null
                }
            }

            DisposableEffect(useDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (useDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose {}
            }

            BrainBufferTheme(
                themeMode = uiState.preferences.themeMode,
                reducedMotionOverride = uiState.preferences.reducedMotionOverride,
                performanceOverride = uiState.preferences.performanceOverride,
                isAppInForeground = isInForeground
            ) {
                BrainBufferApp(
                    viewModel = viewModel,
                    navigationState = navigationState,
                    openControlledAccessRequest = openControlledAccess,
                    onControlledAccessRequestConsumed = { pendingOpenControlledAccess.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(BrainBufferNotificationManager.EXTRA_OPEN_ROUTE)?.let { route ->
            pendingNotificationRoute.value = route
        }
        if (intent.getBooleanExtra(BrainBufferNotificationManager.EXTRA_OPEN_CONTROLLED_ACCESS, false)) {
            pendingOpenControlledAccess.value = true
        }
    }

    override fun onStart() {
        super.onStart()
        isAppInForegroundState.value = true
    }

    override fun onStop() {
        isAppInForegroundState.value = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncTimerOnResume()
        viewModel.refreshAppUsageSuggestion()
    }
}
