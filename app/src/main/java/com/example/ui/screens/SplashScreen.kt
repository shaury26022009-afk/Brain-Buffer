package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.BrainBufferLogoMark
import com.example.ui.theme.BrainBufferThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    isPreferencesLoaded: Boolean,
    onboardingCompleted: Boolean,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val reducedMotion = BrainBufferThemeTokens.reducedMotion
    val logoScale = remember { Animatable(if (reducedMotion) 1f else 0.84f) }
    val logoAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val ringSweep = remember { Animatable(if (reducedMotion) 1f else 0.15f) }
    val glowStrength = remember { Animatable(if (reducedMotion) 0.20f else 0f) }
    val wordmarkAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val wordmarkOffsetY = remember { Animatable(if (reducedMotion) 0f else 18f) }
    val taglineAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            launch { logoAlpha.animateTo(1f, tween(340, easing = FastOutSlowInEasing)) }
            launch {
                logoScale.animateTo(
                    1f,
                    spring(dampingRatio = 0.72f, stiffness = 260f)
                )
            }
            launch { ringSweep.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) }
            launch { glowStrength.animateTo(0.28f, tween(520, easing = FastOutSlowInEasing)) }
            launch {
                delay(220L)
                launch { wordmarkAlpha.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
                launch {
                    wordmarkOffsetY.animateTo(
                        0f,
                        spring(dampingRatio = 0.82f, stiffness = 300f)
                    )
                }
            }
            launch {
                delay(400L)
                taglineAlpha.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
            }
        }
    }

    LaunchedEffect(isPreferencesLoaded) {
        if (!isPreferencesLoaded) return@LaunchedEffect
        delay(if (reducedMotion) 280L else 1080L)
        if (onboardingCompleted) {
            onNavigateToHome()
        } else {
            onNavigateToOnboarding()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("splash_screen")
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrainBufferLogoMark(
                size = 104.dp,
                animatePulse = false,
                ringSweepFraction = ringSweep.value,
                glowAlpha = glowStrength.value,
                modifier = Modifier.graphicsLayer {
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                    alpha = logoAlpha.value
                }
            )
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = stringResource(R.string.app_brand_display),
                style = MaterialTheme.typography.headlineMedium.copy(
                    letterSpacing = 3.6.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = wordmarkAlpha.value
                    translationY = wordmarkOffsetY.value
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(taglineAlpha.value)
            )
        }
    }
}
