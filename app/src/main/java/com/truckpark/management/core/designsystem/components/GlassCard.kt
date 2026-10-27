package com.truckpark.management.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.truckpark.management.core.designsystem.theme.TruckParkExtras

private val CardShape = RoundedCornerShape(28.dp)

/**
 * Liquid-glass style card, built from cheap layers (no per-frame blur):
 *  1. translucent base fill,
 *  2. a diagonal specular sheen from the top-left,
 *  3. a soft accent glow pooling at the bottom edge,
 *  4. a gradient rim: bright where light would catch the edge, fading to a faint accent.
 * It reads as glass because [AppBackground] places soft coloured light behind it.
 * True backdrop blur/refraction is a planned, optional upgrade.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extras = TruckParkExtras.colors
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    // In HTML:
    // border: 1px solid rgba(255, 255, 255, 0.5) [light] / rgba(255, 255, 255, 0.08) [dark]
    // box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.07) [light] / rgba(0,0,0, 0.37) [dark]
    // ::before specular sheen at 45deg: linear-gradient(45deg, transparent 45%, rgba(255,255,255,0.15) 50%, transparent 55%)
    // ::after bottom accent glow: radial-gradient(ellipse at center, rgba(56, 189, 248, 0.3) 0%, rgba(0,0,0,0) 70%)
    val sheen = Color.White.copy(alpha = if (dark) 0.12f else 0.28f)
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.25f else 0.65f),
        0.4f to extras.glassBorder,
        1f to scheme.primary.copy(alpha = if (dark) 0.15f else 0.25f),
    )

    val shadowColor = if (dark) Color.Black.copy(alpha = 0.45f) else Color(0xFF1F2687).copy(alpha = 0.10f)
    val bottomGlow = Color(0xFF38BDF8).copy(alpha = if (dark) 0.35f else 0.22f)

    Box(
        modifier = modifier
            .drawBehind {
                // Soft liquid glass outer shadow: 0 8px 32px
                drawRoundRect(
                    color = shadowColor,
                    topLeft = Offset(0f, 6.dp.toPx()),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()),
                )
                // Bottom Accent Glow pool (HTML .glass-card::after)
                val glowWidth = size.width * 0.8f
                val glowHeight = 28.dp.toPx()
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(bottomGlow, Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height + (glowHeight * 0.2f)),
                        radius = glowWidth * 0.55f,
                    ),
                    topLeft = Offset(size.width * 0.1f, size.height - (glowHeight * 0.6f)),
                    size = androidx.compose.ui.geometry.Size(glowWidth, glowHeight * 1.6f),
                )
            },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, rim),
        ) {
            Box(
                Modifier
                    .background(extras.glassFill)
                    // Diagonal Specular Sheen (matching HTML .glass-card::before)
                    .background(
                        Brush.linearGradient(
                            0f to sheen,
                            0.35f to sheen.copy(alpha = sheen.alpha * 0.5f),
                            0.60f to Color.Transparent,
                            start = Offset(0f, 0f),
                            end = Offset(400f, 400f),
                        ),
                    )
                    // Inset highlight shimmer at top rim
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (dark) 0.08f else 0.25f),
                            0.12f to Color.Transparent,
                        ),
                    ),
            ) {
                Column(Modifier.padding(contentPadding.dp), content = content)
            }
        }
    }
}

/**
 * Full-screen backdrop mirroring HTML:
 * light-pool-1: top-left (-10%, -10%), radial-gradient rgba(56, 189, 248, 0.25) [Sky 400]
 * light-pool-2: bottom-right (-10%, -10%), radial-gradient rgba(99, 102, 241, 0.25) [Indigo 500]
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val extras = TruckParkExtras.colors
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    // Light pools matching HTML CSS:
    // .light-pool-1: rgba(56, 189, 248, 0.25) (Sky 400)
    // .light-pool-2: rgba(99, 102, 241, 0.25) (Indigo 500)
    val skyPool = Color(0xFF38BDF8).copy(alpha = if (dark) 0.22f else 0.18f)
    val indigoPool = Color(0xFF6366F1).copy(alpha = if (dark) 0.24f else 0.18f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(extras.backgroundTop, extras.backgroundBottom)))
            .drawBehind {
                // light-pool-1 (top-left)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(skyPool, Color.Transparent),
                        center = Offset(size.width * 0.05f, size.height * 0.05f),
                        radius = size.width * 0.85f,
                    ),
                )
                // light-pool-2 (bottom-right)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(indigoPool, Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.95f),
                        radius = size.width * 0.95f,
                    ),
                )
            },
    ) {
        content()
    }
}
