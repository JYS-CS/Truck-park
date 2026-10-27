package com.truckpark.management.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom icon set, hand-drawn on a 24x24 grid as thin, geometric outlines:
 * one stroke weight (1.6), round caps/joins, sharp angular silhouettes, and a single
 * small "node" dot as the accent. Colour comes from the Icon tint, so they follow the theme.
 */
object TruckParkIcons {

    private const val STROKE = 1.6f
    private const val DOT = 2.6f

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private fun ImageVector.Builder.line(width: Float = STROKE, draw: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw,
        )
    }

    /** A single accent "node": a zero-length stroke with a round cap renders as a dot. */
    private fun ImageVector.Builder.dot(x: Float, y: Float) = line(DOT) {
        moveTo(x, y)
        lineTo(x + 0.01f, y)
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }

    val Home: ImageVector by lazy {
        icon("Home") {
            line {
                moveTo(3f, 11.2f); lineTo(12f, 3.8f); lineTo(21f, 11.2f)
                moveTo(5.5f, 9.6f); verticalLineTo(20f); horizontalLineTo(18.5f); verticalLineTo(9.6f)
                moveTo(9.8f, 20f); verticalLineTo(14.5f); horizontalLineTo(14.2f); verticalLineTo(20f)
            }
            dot(12f, 9f)
        }
    }

    /** Visits: a truck in side profile. */
    val Truck: ImageVector by lazy {
        icon("Truck") {
            line {
                moveTo(2.5f, 6.5f); horizontalLineTo(14f); verticalLineTo(16f); horizontalLineTo(2.5f); close()
                moveTo(14f, 9.5f); horizontalLineTo(18f); lineTo(21.5f, 12.8f); verticalLineTo(16f); horizontalLineTo(14f)
                circle(7f, 17.6f, 1.9f)
                circle(17.6f, 17.6f, 1.9f)
            }
            dot(8.2f, 11.2f)
        }
    }

    /** Registers: three stacked planes. */
    val Registers: ImageVector by lazy {
        icon("Registers") {
            line {
                moveTo(12f, 3f); lineTo(20.5f, 7.5f); lineTo(12f, 12f); lineTo(3.5f, 7.5f); close()
                moveTo(3.5f, 11.8f); lineTo(12f, 16.3f); lineTo(20.5f, 11.8f)
                moveTo(3.5f, 16f); lineTo(12f, 20.5f); lineTo(20.5f, 16f)
            }
            dot(12f, 7.5f)
        }
    }

    val Reports: ImageVector by lazy {
        icon("Reports") {
            line {
                moveTo(4f, 3.5f); verticalLineTo(20f); horizontalLineTo(20.5f)
                moveTo(8.5f, 16f); verticalLineTo(12.5f)
                moveTo(12.5f, 16f); verticalLineTo(8.5f)
                moveTo(16.5f, 16f); verticalLineTo(10.8f)
            }
            dot(16.5f, 6.2f)
        }
    }

    val More: ImageVector by lazy {
        icon("More") {
            line(DOT) {
                moveTo(5.5f, 12f); lineTo(5.51f, 12f)
                moveTo(12f, 12f); lineTo(12.01f, 12f)
                moveTo(18.5f, 12f); lineTo(18.51f, 12f)
            }
        }
    }

    // ---- Status icons ----

    val Success: ImageVector by lazy {
        icon("Success") {
            line { circle(12f, 12f, 8.8f); moveTo(8f, 12.4f); lineTo(10.9f, 15.2f); lineTo(16f, 9.2f) }
        }
    }

    val Info: ImageVector by lazy {
        icon("Info") {
            line { circle(12f, 12f, 8.8f); moveTo(12f, 11f); verticalLineTo(16.4f) }
            dot(12f, 7.9f)
        }
    }

    val Warning: ImageVector by lazy {
        icon("Warning") {
            line {
                moveTo(12f, 3.6f); lineTo(21.4f, 19.8f); horizontalLineTo(2.6f); close()
                moveTo(12f, 10f); verticalLineTo(14.2f)
            }
            dot(12f, 16.9f)
        }
    }

    /** Critical: an octagon, deliberately distinct from Warning's triangle. */
    val Critical: ImageVector by lazy {
        icon("Critical") {
            line {
                moveTo(8.6f, 3f); horizontalLineTo(15.4f); lineTo(21f, 8.6f); verticalLineTo(15.4f)
                lineTo(15.4f, 21f); horizontalLineTo(8.6f); lineTo(3f, 15.4f); verticalLineTo(8.6f); close()
                moveTo(12f, 7.8f); verticalLineTo(12.6f)
            }
            dot(12f, 16f)
        }
    }

    val Neutral: ImageVector by lazy {
        icon("Neutral") {
            line { circle(12f, 12f, 8.8f); moveTo(8.2f, 12f); horizontalLineTo(15.8f) }
        }
    }

    /** Offline: signal arcs struck through. */
    val Offline: ImageVector by lazy {
        icon("Offline") {
            line {
                moveTo(3f, 9.6f); quadTo(12f, 2.6f, 21f, 9.6f)
                moveTo(6.4f, 13.2f); quadTo(12f, 8.8f, 17.6f, 13.2f)
                moveTo(4f, 4f); lineTo(20f, 20f)
            }
            dot(12f, 17.6f)
        }
    }
}
