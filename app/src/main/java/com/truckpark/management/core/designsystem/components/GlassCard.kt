package com.truckpark.management.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
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
    contentPadding: Int = 20,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extras = TruckParkExtras.colors
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    val sheen = Color.White.copy(alpha = if (dark) 0.13f else 0.70f)
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.55f else 1f),
        0.4f to extras.glassBorder,
        1f to scheme.primary.copy(alpha = if (dark) 0.40f else 0.30f),
    )

    Surface(
        modifier = modifier,
        shape = CardShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, rim),
    ) {
        Box(
            Modifier
                .background(extras.glassFill)
                .background(Brush.linearGradient(0f to sheen, 0.55f to Color.Transparent))
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to scheme.primary.copy(alpha = if (dark) 0.12f else 0.08f),
                    ),
                ),
        ) {
            Column(Modifier.padding(contentPadding.dp), content = content)
        }
    }
}

/**
 * Full-screen backdrop: a deep gradient with two soft pools of cyan and indigo light.
 * The glass cards sit over these, which is what gives them depth.
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val extras = TruckParkExtras.colors
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val cyan = scheme.primary.copy(alpha = if (dark) 0.24f else 0.16f)
    val indigo = scheme.secondary.copy(alpha = if (dark) 0.30f else 0.14f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(extras.backgroundTop, extras.backgroundBottom)))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(cyan, Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.04f),
                        radius = size.width * 0.95f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(indigo, Color.Transparent),
                        center = Offset(size.width * 0.04f, size.height * 0.72f),
                        radius = size.width * 1.1f,
                    ),
                )
            },
    ) {
        content()
    }
}
