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

    private fun ImageVector.Builder.line(width: Float = STROKE, color: Color = Color.White, draw: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = SolidColor(color),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw,
        )
    }

    private fun ImageVector.Builder.fillPath(color: Color, draw: PathBuilder.() -> Unit) {
        path(
            fill = SolidColor(color),
            stroke = null,
            pathBuilder = draw,
        )
    }

    /** A single accent "node": a zero-length stroke with a round cap renders as a dot. */
    private fun ImageVector.Builder.dot(x: Float, y: Float, color: Color = Color.White) = line(DOT, color) {
        moveTo(x, y)
        lineTo(x + 0.01f, y)
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }

    /** Professional Colorful Home: Modern Indigo roof & structure with Sky-cyan glass door and soft ambient fill. */
    fun buildHomeIcon(tintColor: Color, isSelected: Boolean): ImageVector {
        val primary = if (isSelected) Color(0xFF6366F1) else Color(0xFF818CF8).copy(alpha = 0.85f) // Indigo 500 / 400
        val secondary = if (isSelected) Color(0xFF38BDF8) else Color(0xFF7DD3FC).copy(alpha = 0.85f) // Sky 400 / 300
        val fillAlpha = if (isSelected) 0.22f else 0.12f

        return icon("Home_Colorful") {
            // Soft translucent base body fill
            fillPath(primary.copy(alpha = fillAlpha)) {
                moveTo(5.5f, 10.5f)
                lineTo(12f, 4.6f)
                lineTo(18.5f, 10.5f)
                verticalLineTo(19.2f)
                curveToRelative(0f, 0.7f, -0.5f, 1.3f, -1.2f, 1.3f)
                horizontalLineTo(6.7f)
                curveToRelative(-0.7f, 0f, -1.2f, -0.6f, -1.2f, -1.3f)
                close()
            }
            // Crisp roof gables line
            line(width = 2.0f, color = primary) {
                moveTo(2.8f, 11.2f)
                lineTo(12f, 3.8f)
                lineTo(21.2f, 11.2f)
            }
            // Walls and base outline
            line(width = 1.6f, color = primary) {
                moveTo(5.5f, 10.2f)
                verticalLineTo(19.2f)
                curveToRelative(0f, 0.6f, 0.4f, 1.1f, 1f, 1.1f)
                horizontalLineTo(17.5f)
                curveToRelative(0.6f, 0f, 1f, -0.5f, 1f, -1.1f)
                verticalLineTo(10.2f)
            }
            // Vibrant Cyan Glass Door & Light Window
            fillPath(secondary.copy(alpha = if (isSelected) 0.85f else 0.55f)) {
                moveTo(10f, 15f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                verticalLineTo(20.3f)
                horizontalLineTo(10f)
                close()
            }
            line(width = 1.5f, color = secondary) {
                moveTo(10f, 20.3f)
                verticalLineTo(15f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                verticalLineTo(20.3f)
            }
            dot(12f, 8.5f, secondary)
        }
    }

    /** Professional Colorful Visits/Truck: Emerald green cargo chassis with Mint-teal aerodynamic cab and wheels. */
    fun buildTruckIcon(tintColor: Color, isSelected: Boolean): ImageVector {
        val emerald = if (isSelected) Color(0xFF10B981) else Color(0xFF34D399).copy(alpha = 0.85f) // Emerald 500 / 400
        val mint = if (isSelected) Color(0xFF14B8A6) else Color(0xFF2DD4BF).copy(alpha = 0.85f) // Teal 500 / 400
        val fillAlpha = if (isSelected) 0.22f else 0.12f

        return icon("Truck_Colorful") {
            // Soft cargo container fill
            fillPath(emerald.copy(alpha = fillAlpha)) {
                moveTo(2.5f, 6.8f)
                curveToRelative(0f, -0.6f, 0.5f, -1.1f, 1.1f, -1.1f)
                horizontalLineTo(13.4f)
                curveToRelative(0.6f, 0f, 1.1f, 0.5f, 1.1f, 1.1f)
                verticalLineTo(15.8f)
                horizontalLineTo(2.5f)
                close()
            }
            // Cab fill
            fillPath(mint.copy(alpha = fillAlpha * 1.3f)) {
                moveTo(14.5f, 9.8f)
                horizontalLineTo(18.2f)
                curveToRelative(0.4f, 0f, 0.8f, 0.2f, 1f, 0.5f)
                lineTo(21.6f, 13f)
                curveToRelative(0.3f, 0.4f, 0.4f, 0.8f, 0.4f, 1.2f)
                verticalLineTo(15.8f)
                horizontalLineTo(14.5f)
                close()
            }
            // Cargo trailer outline
            line(width = 1.7f, color = emerald) {
                moveTo(2.5f, 6.8f)
                curveToRelative(0f, -0.6f, 0.5f, -1.1f, 1.1f, -1.1f)
                horizontalLineTo(13.4f)
                curveToRelative(0.6f, 0f, 1.1f, 0.5f, 1.1f, 1.1f)
                verticalLineTo(15.8f)
                horizontalLineTo(2.5f)
                close()
                // Trailer inner rib line
                moveTo(6.5f, 7f); verticalLineTo(14f)
                moveTo(10.5f, 7f); verticalLineTo(14f)
            }
            // Cab outline
            line(width = 1.7f, color = mint) {
                moveTo(14.5f, 9.8f)
                horizontalLineTo(18.2f)
                lineTo(21.8f, 13.5f)
                verticalLineTo(15.8f)
                horizontalLineTo(14.5f)
                moveTo(18f, 9.8f); verticalLineTo(13.5f); horizontalLineTo(21.4f)
            }
            // Wheels with distinct dark rim and bright mint hubcaps
            fillPath(emerald) {
                circle(7.2f, 17.5f, 2.2f)
                circle(17.8f, 17.5f, 2.2f)
            }
            fillPath(Color.White) {
                circle(7.2f, 17.5f, 0.8f)
                circle(17.8f, 17.5f, 0.8f)
            }
        }
    }

    /** Professional Colorful Registers: Layered Amber/Orange documents and clipboard binder with vivid badge. */
    fun buildRegistersIcon(tintColor: Color, isSelected: Boolean): ImageVector {
        val amber = if (isSelected) Color(0xFFF59E0B) else Color(0xFFFBBF24).copy(alpha = 0.85f) // Amber 500 / 400
        val orange = if (isSelected) Color(0xFFEA580C) else Color(0xFFFB923C).copy(alpha = 0.85f) // Orange 600 / 400
        val fillAlpha = if (isSelected) 0.22f else 0.12f

        return icon("Registers_Colorful") {
            // Background card tier
            fillPath(orange.copy(alpha = fillAlpha * 0.7f)) {
                moveTo(4.5f, 4.5f)
                horizontalLineTo(19.5f)
                verticalLineTo(8.5f)
                horizontalLineTo(4.5f)
                close()
            }
            line(width = 1.4f, color = orange.copy(alpha = 0.6f)) {
                moveTo(5f, 4.5f)
                horizontalLineTo(19f)
            }
            // Main clipboard/register card
            fillPath(amber.copy(alpha = fillAlpha)) {
                moveTo(4f, 8f)
                curveToRelative(0f, -0.8f, 0.6f, -1.5f, 1.5f, -1.5f)
                horizontalLineTo(18.5f)
                curveToRelative(0.8f, 0f, 1.5f, 0.6f, 1.5f, 1.5f)
                verticalLineTo(20f)
                curveToRelative(0f, 0.8f, -0.6f, 1.5f, -1.5f, 1.5f)
                horizontalLineTo(5.5f)
                curveToRelative(-0.8f, 0f, -1.5f, -0.6f, -1.5f, -1.5f)
                close()
            }
            line(width = 1.7f, color = amber) {
                moveTo(4f, 8f)
                curveToRelative(0f, -0.8f, 0.6f, -1.5f, 1.5f, -1.5f)
                horizontalLineTo(18.5f)
                curveToRelative(0.8f, 0f, 1.5f, 0.6f, 1.5f, 1.5f)
                verticalLineTo(20f)
                curveToRelative(0f, 0.8f, -0.6f, 1.5f, -1.5f, 1.5f)
                horizontalLineTo(5.5f)
                curveToRelative(-0.8f, 0f, -1.5f, -0.6f, -1.5f, -1.5f)
                close()
            }
            // Document rows
            line(width = 1.5f, color = amber) {
                moveTo(7.5f, 11f); horizontalLineTo(16.5f)
                moveTo(7.5f, 14.5f); horizontalLineTo(16.5f)
                moveTo(7.5f, 18f); horizontalLineTo(12.5f)
            }
            // Top Binder Clip in contrasting vibrant Orange
            fillPath(orange) {
                moveTo(9.5f, 5.5f)
                curveToRelative(0f, -0.8f, 0.6f, -1.5f, 1.5f, -1.5f)
                horizontalLineTo(13f)
                curveToRelative(0.8f, 0f, 1.5f, 0.6f, 1.5f, 1.5f)
                verticalLineTo(8f)
                horizontalLineTo(9.5f)
                close()
            }
            line(width = 1.4f, color = orange) {
                moveTo(9f, 6.5f); horizontalLineTo(15f)
            }
        }
    }

    /** Professional Colorful Reports: Vibrant Blue analytics report page with ascending Cyan growth chart bars. */
    fun buildReportsIcon(tintColor: Color, isSelected: Boolean): ImageVector {
        val blue = if (isSelected) Color(0xFF2563EB) else Color(0xFF60A5FA).copy(alpha = 0.85f) // Blue 600 / 400
        val cyan = if (isSelected) Color(0xFF06B6D4) else Color(0xFF22D3EE).copy(alpha = 0.85f) // Cyan 500 / 400
        val fillAlpha = if (isSelected) 0.22f else 0.12f

        return icon("Reports_Colorful") {
            // Translucent report document fill with folded ear corner
            fillPath(blue.copy(alpha = fillAlpha)) {
                moveTo(4.5f, 4.5f)
                curveToRelative(0f, -0.8f, 0.6f, -1.5f, 1.5f, -1.5f)
                horizontalLineTo(14f)
                lineTo(19.5f, 8.5f)
                verticalLineTo(20f)
                curveToRelative(0f, 0.8f, -0.6f, 1.5f, -1.5f, 1.5f)
                horizontalLineTo(6f)
                curveToRelative(-0.8f, 0f, -1.5f, -0.6f, -1.5f, -1.5f)
                close()
            }
            // Sheet outline
            line(width = 1.7f, color = blue) {
                moveTo(14f, 3f)
                horizontalLineTo(6f)
                curveToRelative(-0.8f, 0f, -1.5f, 0.6f, -1.5f, 1.5f)
                verticalLineTo(20f)
                curveToRelative(0f, 0.8f, 0.6f, 1.5f, 1.5f, 1.5f)
                horizontalLineTo(18f)
                curveToRelative(0.8f, 0f, 1.5f, -0.6f, 1.5f, -1.5f)
                verticalLineTo(8.5f)
                lineTo(14f, 3f)
                close()
                // Folded corner
                moveTo(14f, 3f); verticalLineTo(8.5f); horizontalLineTo(19.5f)
            }
            // Ascending colorful bar chart bars (Cyan/Blue gradient appearance)
            fillPath(cyan.copy(alpha = if (isSelected) 0.85f else 0.55f)) {
                // Bar 1 (Short)
                moveTo(7.5f, 14.5f)
                curveToRelative(0f, -0.4f, 0.3f, -0.7f, 0.7f, -0.7f)
                horizontalLineTo(9f)
                curveToRelative(0.4f, 0f, 0.7f, 0.3f, 0.7f, 0.7f)
                verticalLineTo(18.5f)
                horizontalLineTo(7.5f)
                close()
                // Bar 2 (Medium)
                moveTo(11f, 12f)
                curveToRelative(0f, -0.4f, 0.3f, -0.7f, 0.7f, -0.7f)
                horizontalLineTo(12.5f)
                curveToRelative(0.4f, 0f, 0.7f, 0.3f, 0.7f, 0.7f)
                verticalLineTo(18.5f)
                horizontalLineTo(11f)
                close()
                // Bar 3 (Tall peak)
                moveTo(14.5f, 9.5f)
                curveToRelative(0f, -0.4f, 0.3f, -0.7f, 0.7f, -0.7f)
                horizontalLineTo(16f)
                curveToRelative(0.4f, 0f, 0.7f, 0.3f, 0.7f, 0.7f)
                verticalLineTo(18.5f)
                horizontalLineTo(14.5f)
                close()
            }
            line(width = 1.3f, color = cyan) {
                moveTo(7.5f, 18.5f); horizontalLineTo(16.5f)
            }
        }
    }

    val Home: ImageVector by lazy { buildHomeIcon(Color.White, true) }
    val Truck: ImageVector by lazy { buildTruckIcon(Color.White, true) }
    val Registers: ImageVector by lazy { buildRegistersIcon(Color.White, true) }
    val Reports: ImageVector by lazy { buildReportsIcon(Color.White, true) }

    val More: ImageVector by lazy {
        icon("More") {
            line(DOT) {
                moveTo(5.5f, 12f); lineTo(5.51f, 12f)
                moveTo(12f, 12f); lineTo(12.01f, 12f)
                moveTo(18.5f, 12f); lineTo(18.51f, 12f)
            }
        }
    }

    /** Top-right Hamburger Menu icon matching HTML `<path d="M4 6h16M4 12h16M4 18h16"/>`. */
    val Menu: ImageVector by lazy {
        icon("Menu") {
            line {
                moveTo(4f, 6f); lineTo(20f, 6f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 18f); lineTo(20f, 18f)
            }
        }
    }

    /** Theme icons for Appearance selector **/
    val ThemeSystem: ImageVector by lazy {
        icon("ThemeSystem") {
            // Monitor / System Display outline with desktop stand
            line(width = 1.6f, color = Color(0xFF6366F1)) {
                moveTo(3f, 4.5f)
                horizontalLineTo(21f)
                verticalLineTo(15.5f)
                horizontalLineTo(3f)
                close()
                // Stand & Base
                moveTo(8.5f, 19.5f); horizontalLineTo(15.5f)
                moveTo(12f, 15.5f); verticalLineTo(19.5f)
            }
            // Half-tone display screen
            fillPath(Color(0xFF38BDF8).copy(alpha = 0.35f)) {
                moveTo(4.5f, 6f); horizontalLineTo(12f); verticalLineTo(14f); horizontalLineTo(4.5f); close()
            }
        }
    }

    val ThemeLight: ImageVector by lazy {
        icon("ThemeLight") {
            // Warm sunrise sun with rich amber-orange core and subtle radiance
            val sunCore = Color(0xFFEA580C) // Rich warm orange-amber (Orange 600)
            val sunRay = Color(0xFFF97316)  // Radiant warm amber (Orange 500)
            fillPath(sunRay.copy(alpha = 0.22f)) {
                circle(12f, 12f, 4.5f)
            }
            line(width = 1.7f, color = sunCore) {
                circle(12f, 12f, 4.2f)
            }
            line(width = 1.6f, color = sunRay) {
                // Radiating cardinal & diagonal rays
                moveTo(12f, 2.5f); verticalLineTo(4.5f)
                moveTo(12f, 19.5f); verticalLineTo(21.5f)
                moveTo(2.5f, 12f); horizontalLineTo(4.5f)
                moveTo(19.5f, 12f); horizontalLineTo(21.5f)
                moveTo(5.3f, 5.3f); lineTo(6.7f, 6.7f)
                moveTo(17.3f, 17.3f); lineTo(18.7f, 18.7f)
                moveTo(5.3f, 18.7f); lineTo(6.7f, 17.3f)
                moveTo(17.3f, 6.7f); lineTo(18.7f, 5.3f)
            }
        }
    }

    val ThemeDark: ImageVector by lazy {
        icon("ThemeDark") {
            // Crescent Moon with twinkle star
            fillPath(Color(0xFF818CF8).copy(alpha = 0.25f)) {
                moveTo(20.4f, 14.5f)
                curveToRelative(-1f, 0.4f, -2.1f, 0.6f, -3.2f, 0.6f)
                curveToRelative(-4.8f, 0f, -8.7f, -3.9f, -8.7f, -8.7f)
                curveToRelative(0f, -1.8f, 0.5f, -3.4f, 1.5f, -4.8f)
                curveTo(5.6f, 2.6f, 2.4f, 6.6f, 2.4f, 11.5f)
                curveTo(2.4f, 17f, 6.9f, 21.5f, 12.4f, 21.5f)
                curveToRelative(4.5f, 0f, 8.2f, -2.8f, 9.6f, -6.8f)
                close()
            }
            line(width = 1.7f, color = Color(0xFF818CF8)) {
                moveTo(20.4f, 14.5f)
                curveToRelative(-1f, 0.4f, -2.1f, 0.6f, -3.2f, 0.6f)
                curveToRelative(-4.8f, 0f, -8.7f, -3.9f, -8.7f, -8.7f)
                curveToRelative(0f, -1.8f, 0.5f, -3.4f, 1.5f, -4.8f)
                curveTo(5.6f, 2.6f, 2.4f, 6.6f, 2.4f, 11.5f)
                curveTo(2.4f, 17f, 6.9f, 21.5f, 12.4f, 21.5f)
                curveToRelative(4.5f, 0f, 8.2f, -2.8f, 9.6f, -6.8f)
                close()
            }
            dot(17.5f, 6f, Color(0xFF38BDF8))
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
