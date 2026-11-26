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
import androidx.compose.runtime.mutableIntStateOf
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
    private var wasInBackground = false
    private val foregroundResumeSignalState = mutableIntStateOf(0)
    private val pendingNotificationRoute = mutableStateOf<String?>(null)
    private val pendingOpenControlledAccess = mutableStateOf(false)
    private val pendingOpenChangePurpose = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BrainBufferNotificationManager.ensureChannels(applicationContext)
        pendingNotificationRoute.value = intent?.getStringExtra(BrainBufferNotificationManager.EXTRA_OPEN_ROUTE)
        pendingOpenControlledAccess.value = intent?.getBooleanExtra(
            BrainBufferNotificationManager.EXTRA_OPEN_CONTROLLED_ACCESS,
            false
        ) ?: false
        pendingOpenChangePurpose.value = intent?.getBooleanExtra(
            BrainBufferNotificationManager.EXTRA_OPEN_CHANGE_PURPOSE,
            false
        ) ?: false
        handleAuthRedirectIfPresent(intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val shellState by viewModel.themeShellState.collectAsStateWithLifecycle()
            val navigationState = rememberBrainBufferNavigationState()
            val systemDark = isSystemInDarkTheme()
            val useDark = when (shellState.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            val isInForeground by isAppInForegroundState
            val foregroundResumeSignal by foregroundResumeSignalState
            val deepLinkRoute by pendingNotificationRoute
            val openControlledAccess by pendingOpenControlledAccess
            val openChangePurpose by pendingOpenChangePurpose

            LaunchedEffect(deepLinkRoute, shellState.isLoaded, shellState.onboardingCompleted) {
                if (deepLinkRoute != null && shellState.isLoaded && shellState.onboardingCompleted) {
                    when (deepLinkRoute) {
                        AppRoutes.STRICT_FOCUS, AppRoutes.FOCUS, AppRoutes.BLOCK ->
                            navigationState.navigateToTopLevel(BottomNavDestination.FOCUS)
                        AppRoutes.INSIGHTS, AppRoutes.USAGE ->
                            navigationState.navigateToTopLevel(BottomNavDestination.INSIGHTS)
                        AppRoutes.PROFILE, AppRoutes.SETTINGS ->
                            navigationState.navigateToTopLevel(BottomNavDestination.SETTINGS)
                        else -> navigationState.navigateToTopLevel(BottomNavDestination.HOME)
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
                themeMode = shellState.themeMode,
                reducedMotionOverride = shellState.reducedMotionOverride,
                performanceOverride = shellState.performanceOverride,
                isAppInForeground = isInForeground
            ) {
                BrainBufferApp(
                    viewModel = viewModel,
                    navigationState = navigationState,
                    foregroundResumeSignal = foregroundResumeSignal,
                    openControlledAccessRequest = openControlledAccess,
                    onControlledAccessRequestConsumed = { pendingOpenControlledAccess.value = false },
                    openChangePurposeRequest = openChangePurpose,
                    onChangePurposeRequestConsumed = { pendingOpenChangePurpose.value = false }
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
        if (intent.getBooleanExtra(BrainBufferNotificationManager.EXTRA_OPEN_CHANGE_PURPOSE, false)) {
            pendingOpenChangePurpose.value = true
        }
        handleAuthRedirectIfPresent(intent)
    }

    private fun handleAuthRedirectIfPresent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == com.example.cloud.SupabaseConfig.AUTH_REDIRECT_SCHEME &&
            uri.host == com.example.cloud.SupabaseConfig.AUTH_REDIRECT_HOST
        ) {
            viewModel.handleAuthCallbackUri(uri)
        }
    }

    override fun onStart() {
        super.onStart()
        isAppInForegroundState.value = true
        if (wasInBackground) {
            wasInBackground = false
            foregroundResumeSignalState.intValue += 1
        }
    }

    override fun onStop() {
        isAppInForegroundState.value = false
        wasInBackground = true
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncTimerOnResume()
        viewModel.refreshAppUsageSuggestion()
    }
}
