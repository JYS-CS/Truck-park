package com.truckpark.management.feature.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckpark.management.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Premium Hibiscus animated liquid glass splash screen.
 * Features:
 * - Fluid radial backlight pulse
 * - Glossy "H" emblem floating spring-in scale and gentle levitation
 * - High-end specular beam sweep across the emblem
 * - Synchronized glowing "HIBISCUSS" wordmark fade-in with letter tracking
 * - Smooth fade-out handoff to the main app dashboard
 */
@Composable
fun HibiscusAnimatedSplashScreen(
    onSplashFinished: () -> Unit,
) {
    val emblemScale = remember { Animatable(0.70f) }
    val emblemAlpha = remember { Animatable(0f) }
    val wordmarkAlpha = remember { Animatable(0f) }
    val wordmarkOffset = remember { Animatable(24f) }
    val underlineScale = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val glowPulse = remember { Animatable(0.40f) }
    val shimmerOffset = remember { Animatable(-1f) }
    val screenAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Continuous ambient glow breathing
        launch {
            glowPulse.animateTo(
                targetValue = 0.90f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        }

        // Shimmer sweep across the wordmark & emblem
        launch {
            delay(500)
            shimmerOffset.animateTo(
                targetValue = 2f,
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            )
        }

        // 1. Emblem blooms and rises into place with liquid spring
        launch {
            emblemAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(600, easing = FastOutSlowInEasing),
            )
        }
        emblemScale.animateTo(
            targetValue = 1.05f,
            animationSpec = tween(650, easing = FastOutSlowInEasing),
        )
        emblemScale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(250, easing = LinearEasing),
        )

        // 2. Wordmark fades up with a slight upward slide
        delay(120)
        launch {
            wordmarkAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(500, easing = FastOutSlowInEasing),
            )
        }
        wordmarkOffset.animateTo(
            targetValue = 0f,
            animationSpec = tween(500, easing = FastOutSlowInEasing),
        )

        // 3. Cyan underline expands from center
        launch {
            underlineScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(450, easing = FastOutSlowInEasing),
            )
        }

        // 4. Subtitle / tagline reveals
        subtitleAlpha.animateTo(
            targetValue = 0.85f,
            animationSpec = tween(400, easing = FastOutSlowInEasing),
        )

        // Hold momentarily to wow the viewer, then smoothly fade out
        delay(750)
        screenAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(400, easing = FastOutSlowInEasing),
        )
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlpha.value)
            .background(Color(0xFF05071E)), // Exact Hibiscus dark navy background
        contentAlignment = Alignment.Center,
    ) {
        // Ambient Liquid Cyan / Electric Indigo radial glow pool behind emblem
        Box(
            modifier = Modifier
                .size(360.dp)
                .scale(emblemScale.value)
                .alpha(glowPulse.value)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00F5FF).copy(alpha = 0.40f),
                            Color(0xFF26BFFF).copy(alpha = 0.25f),
                            Color(0xFF4938FF).copy(alpha = 0.15f),
                            Color(0xFFC500FF).copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxSize(),
        ) {
            // High-resolution Glossy H Emblem with Highway curve
            Image(
                painter = painterResource(id = R.drawable.hibiscus_emblem_trans),
                contentDescription = "Hibiscus Emblem",
                modifier = Modifier
                    .size(190.dp)
                    .scale(emblemScale.value)
                    .alpha(emblemAlpha.value),
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Glossy HIBISCUSS Wordmark with Shimmer Light Sweep
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(280.dp)
                    .offset(y = wordmarkOffset.value.dp)
                    .alpha(wordmarkAlpha.value),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hibiscus_wordmark_trans),
                    contentDescription = "HIBISCUSS",
                    modifier = Modifier.fillMaxSize(),
                )

                // Shimmer light beam sweep
                if (shimmerOffset.value in -0.5f..1.5f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.horizontalGradient(
                                    0.0f to Color.Transparent,
                                    (shimmerOffset.value - 0.2f).coerceIn(0f, 1f) to Color.Transparent,
                                    shimmerOffset.value.coerceIn(0f, 1f) to Color.White.copy(alpha = 0.35f),
                                    (shimmerOffset.value + 0.2f).coerceIn(0f, 1f) to Color.Transparent,
                                    1.0f to Color.Transparent,
                                ),
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cyan luminous underline (from the SVG brand guide)
            Box(
                modifier = Modifier
                    .width(160.dp * underlineScale.value)
                    .height(2.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF00DFFF),
                                Color(0xFF00F5FF),
                                Color(0xFF00DFFF),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Subtitle Tagline
            Text(
                text = "TRUCK PARK MANAGEMENT",
                color = Color(0xFFA9C8FF).copy(alpha = subtitleAlpha.value),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.5.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.alpha(subtitleAlpha.value),
            )
        }
    }
}
