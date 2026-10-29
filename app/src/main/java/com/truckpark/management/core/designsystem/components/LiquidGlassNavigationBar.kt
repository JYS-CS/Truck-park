package com.truckpark.management.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckpark.management.core.designsystem.icons.TruckParkIcons
import com.truckpark.management.core.designsystem.theme.TruckParkExtras
import com.truckpark.management.navigation.TopLevelDestination

private val NavCapsuleShape = RoundedCornerShape(32.dp)
private val IndicatorShape = RoundedCornerShape(24.dp)

/**
 * Floating iOS-style liquid glass navigation bar.
 * Features:
 *  - Floating translucent pill container with specular rim & ambient light glow
 *  - Continuous liquid pill indicator that smoothly glides and stretches across tabs
 *    (e.g., from Reports -> Registers -> Visits -> Home)
 *  - Distinctive active vs unactive icon styles (scale bump, dot accent, vibrant tint)
 */
@Composable
fun LiquidGlassNavigationBar(
    destinations: List<TopLevelDestination>,
    currentRoute: String?,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = TruckParkExtras.colors
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    val selectedIndex = destinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    // HOISTED ANIMATABLE: Lives across all layout passes and recompositions so it never resets or skips
    val animIndex = remember { androidx.compose.animation.core.Animatable(selectedIndex.toFloat()) }

    // Step-by-step glide: quick visible pass-through intermediate tabs, smooth landing at destination
    suspend fun liquidGlideTo(target: Float) {
        val start = animIndex.value
        if (start == target) return
        val step = if (target > start) 1f else -1f
        var current = start
        while (kotlin.math.abs(target - current) > 0.001f) {
            val next = (current + step).coerceIn(
                minOf(start, target),
                maxOf(start, target),
            )
            val isLast = kotlin.math.abs(target - next) < 0.001f
            animIndex.animateTo(
                targetValue = next,
                animationSpec = androidx.compose.animation.core.tween(
                    // Intermediate tabs: 90ms quick pass — visible but not settled
                    // Final destination: 220ms ease-out — smooth landing only here
                    durationMillis = if (isLast) 220 else 90,
                    easing = if (isLast) androidx.compose.animation.core.FastOutSlowInEasing
                             else androidx.compose.animation.core.LinearEasing,
                ),
            )
            current = next
        }
    }

    androidx.compose.runtime.LaunchedEffect(selectedIndex) {
        val target = selectedIndex.toFloat()
        if (animIndex.targetValue != target) {
            liquidGlideTo(target)
        }
    }

    val shadowColor = if (dark) Color.Black.copy(alpha = 0.50f) else Color(0xFF1F2687).copy(alpha = 0.12f)
    val bottomGlow = Color(0xFF38BDF8).copy(alpha = if (dark) 0.35f else 0.20f)
    val sheen = Color.White.copy(alpha = if (dark) 0.10f else 0.30f)

    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.28f else 0.70f),
        0.5f to extras.glassBorder,
        1f to scheme.primary.copy(alpha = if (dark) 0.20f else 0.30f),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .drawBehind {
                // Soft liquid glass shadow behind floating capsule
                drawRoundRect(
                    color = shadowColor,
                    topLeft = Offset(0f, 6.dp.toPx()),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(32.dp.toPx()),
                )
                // Bottom ambient glow pool
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(bottomGlow, Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height + 4.dp.toPx()),
                        radius = size.width * 0.45f,
                    ),
                    topLeft = Offset(size.width * 0.15f, size.height - 12.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.7f, 24.dp.toPx()),
                )
            },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(68.dp),
            shape = NavCapsuleShape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, rim),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(extras.glassFill)
                    // Specular shimmer
                    .background(Brush.linearGradient(0f to sheen, 0.45f to Color.Transparent))
                    .padding(6.dp),
            ) {
                val totalWidth = maxWidth
                val tabCount = destinations.size
                val tabWidth = totalWidth / tabCount

                val currentAnimatedIndex = animIndex.value
                val indicatorLeft = tabWidth * currentAnimatedIndex

                // Distinctly visible in light mode without wash-out, crystal-clear transparent in dark mode
                val bubbleFill = if (dark) {
                    Color.White.copy(alpha = 0.08f) // Highly transparent in dark mode
                } else {
                    Color(0xFF6366F1).copy(alpha = 0.12f) // Clean, visible translucent indigo tint in light mode
                }

                val bubbleBorder = if (dark) {
                    Color.White.copy(alpha = 0.20f)
                } else {
                    Color(0xFF6366F1).copy(alpha = 0.35f) // Defined colorful border in light mode
                }

                val bubbleShadow = if (dark) {
                    Color.Black.copy(alpha = 0.25f)
                } else {
                    Color(0xFF1E1B4B).copy(alpha = 0.10f) // Crisp soft shadow so the bubble visibly pops on light glass
                }

                val bubbleGlow = if (dark) {
                    Color(0xFF38BDF8).copy(alpha = 0.10f)
                } else {
                    Color(0xFF6366F1).copy(alpha = 0.18f)
                }

                Box(
                    modifier = Modifier
                        .offset(x = indicatorLeft)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .drawBehind {
                            // Soft floating depth shadow for the bubble in light mode
                            drawRoundRect(
                                color = bubbleShadow,
                                topLeft = Offset(0f, 3.dp.toPx()),
                                size = size,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
                            )
                        }
                        .clip(IndicatorShape)
                        .background(bubbleFill)
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    bubbleBorder,
                                    bubbleBorder.copy(alpha = if (dark) 0.05f else 0.15f),
                                ),
                            ),
                            shape = IndicatorShape,
                        )
                        .drawBehind {
                            // Top liquid specular highlight
                            drawRoundRect(
                                Brush.linearGradient(
                                    0f to Color.White.copy(alpha = if (dark) 0.30f else 0.85f),
                                    0.4f to Color.White.copy(alpha = if (dark) 0.08f else 0.35f),
                                    1f to Color.Transparent,
                                ),
                                topLeft = Offset(0f, 0f),
                                size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()),
                            )
                            // Ambient soft glow under bubble
                            drawOval(
                                Brush.radialGradient(
                                    colors = listOf(bubbleGlow, Color.Transparent),
                                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                                    radius = size.width * 0.65f,
                                ),
                            )
                        },
                )

                // Row of Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        // Continuous selection weight based on the floating indicator position
                        val proximity = (1f - Math.abs(currentAnimatedIndex - index)).coerceIn(0f, 1f)
                        val iconScale = 1.0f + (proximity * 0.12f)

                        val baseColor = when (destination) {
                            TopLevelDestination.Home -> Color(0xFF6366F1) // Indigo
                            TopLevelDestination.Visits -> Color(0xFF10B981) // Emerald
                            TopLevelDestination.Registers -> Color(0xFFF59E0B) // Amber
                            TopLevelDestination.Reports -> Color(0xFF2563EB) // Royal Blue
                        }

                        val unselectedTextColor = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        val textColor = androidx.compose.ui.graphics.lerp(unselectedTextColor, baseColor, proximity)

                        val vectorIcon = when (destination) {
                            TopLevelDestination.Home -> TruckParkIcons.buildHomeIcon(baseColor, proximity > 0.5f)
                            TopLevelDestination.Visits -> TruckParkIcons.buildTruckIcon(baseColor, proximity > 0.5f)
                            TopLevelDestination.Registers -> TruckParkIcons.buildRegistersIcon(baseColor, proximity > 0.5f)
                            TopLevelDestination.Reports -> TruckParkIcons.buildReportsIcon(baseColor, proximity > 0.5f)
                        }

                        Column(
                            modifier = Modifier
                                .width(tabWidth)
                                .fillMaxHeight()
                                .clip(IndicatorShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    // The indicator glide is started by the selection effect above,
                                    // so the tap only reports the choice.
                                    onDestinationSelected(destination)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                // Subtle matching colored highlight pool that gently fades in with proximity
                                if (proximity > 0.1f) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        baseColor.copy(alpha = (if (dark) 0.30f else 0.20f) * proximity),
                                                        Color.Transparent,
                                                    ),
                                                ),
                                                shape = CircleShape,
                                            ),
                                    )
                                }

                                Icon(
                                    imageVector = vectorIcon,
                                    contentDescription = stringResource(destination.labelRes),
                                    tint = Color.Unspecified, // Preserves the rich SVG multi-colors!
                                    modifier = Modifier
                                        .size(24.dp)
                                        .scale(iconScale),
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = stringResource(destination.labelRes),
                                color = textColor,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (proximity > 0.6f) FontWeight.Bold else FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
