package com.truckpark.management.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Container + content colour pair for a status. Always shown with an icon and a text label. */
@Immutable
data class StatusColors(val container: Color, val content: Color)

/** Colours that Material 3's ColorScheme has no slot for. */
@Immutable
data class TruckParkColors(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val glassFill: Color,
    val glassHighlight: Color,
    val glassBorder: Color,
    val success: StatusColors,
    val info: StatusColors,
    val warning: StatusColors,
    val critical: StatusColors,
    val neutral: StatusColors,
    val offline: StatusColors,
)

// ---------- Dark: Tailwind Slate 950/900, Indigo 500, Sky/Cyan 400 ----------
val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1), // Indigo 500
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF312E81), // Indigo 900
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF38BDF8), // Sky/Cyan 400
    onSecondary = Color(0xFF0C4A6E),
    secondaryContainer = Color(0xFF0369A1),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFF818CF8), // Indigo 400
    onTertiary = Color(0xFF1E1B4B),
    tertiaryContainer = Color(0xFF3730A3),
    onTertiaryContainer = Color(0xFFEEF2FF),
    background = Color(0xFF020617), // Slate 950
    onBackground = Color(0xFFF1F5F9), // Slate 100
    surface = Color(0xFF0F172A), // Slate 900
    onSurface = Color(0xFFF8FAFC), // Slate 50
    surfaceVariant = Color(0xFF1E293B), // Slate 800
    onSurfaceVariant = Color(0xFF94A3B8), // Slate 400
    surfaceTint = Color(0xFF6366F1),
    surfaceContainerLowest = Color(0xFF020617),
    surfaceContainerLow = Color(0xFF0B1120),
    surfaceContainer = Color(0xFF0F172A), // Slate 900
    surfaceContainerHigh = Color(0xFF1E293B), // Slate 800
    surfaceContainerHighest = Color(0xFF334155), // Slate 700
    outline = Color(0xFF475569), // Slate 600
    outlineVariant = Color(0x1FFFFFFF), // rgba(255, 255, 255, 0.12)
    error = Color(0xFFEF4444), // Tailwind Red 500
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFEE2E2),
)

val DarkExtendedColors = TruckParkColors(
    backgroundTop = Color(0xFF0F172A), // Slate 900
    backgroundBottom = Color(0xFF020617), // Slate 950
    glassFill = Color(0xFF0F172A).copy(alpha = 0.65f), // rgba(15, 23, 42, 0.65)
    glassHighlight = Color(0xFF38BDF8).copy(alpha = 0.25f), // Sky 400 light pool
    glassBorder = Color(0xFFFFFFFF).copy(alpha = 0.10f), // borderDark
    success = StatusColors(Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF34D399)), // Emerald / Success
    info = StatusColors(Color(0xFF3B82F6).copy(alpha = 0.15f), Color(0xFF60A5FA)), // Blue / Info
    warning = StatusColors(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFFBBF24)), // Amber / Warning
    critical = StatusColors(Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFF87171)), // Red / Critical
    neutral = StatusColors(Color(0xFF64748B).copy(alpha = 0.15f), Color(0xFF94A3B8)), // Slate / Neutral
    offline = StatusColors(Color(0xFF475569).copy(alpha = 0.15f), Color(0xFFCBD5E1)), // Slate 600 / Offline
)

// ---------- Light: Tailwind Slate 50/100, Indigo 600, Sky 500 ----------
val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5), // Indigo 600
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0284C7), // Sky 600
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF082F49),
    tertiary = Color(0xFF6366F1), // Indigo 500
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEEF2FF),
    onTertiaryContainer = Color(0xFF312E81),
    background = Color(0xFFF1F5F9), // Slate 100
    onBackground = Color(0xFF0F172A), // Slate 900
    surface = Color(0xFFFFFFFF), // White
    onSurface = Color(0xFF0F172A), // Slate 900
    surfaceVariant = Color(0xFFE2E8F0), // Slate 200
    onSurfaceVariant = Color(0xFF64748B), // Slate 500
    surfaceTint = Color(0xFF4F46E5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC), // Slate 50
    surfaceContainer = Color(0xFFF1F5F9), // Slate 100
    surfaceContainerHigh = Color(0xFFE2E8F0), // Slate 200
    surfaceContainerHighest = Color(0xFFCBD5E1), // Slate 300
    outline = Color(0xFF94A3B8), // Slate 400
    outlineVariant = Color(0x66FFFFFF), // borderLight
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
)

val LightExtendedColors = TruckParkColors(
    backgroundTop = Color(0xFFF8FAFC), // Slate 50
    backgroundBottom = Color(0xFFE2E8F0), // Slate 200
    glassFill = Color(0xFFFFFFFF).copy(alpha = 0.65f), // rgba(255, 255, 255, 0.65)
    glassHighlight = Color(0xFF38BDF8).copy(alpha = 0.20f),
    glassBorder = Color(0xFFFFFFFF).copy(alpha = 0.50f),
    success = StatusColors(Color(0xFFD1FAE5), Color(0xFF047857)), // Emerald 100 / 700
    info = StatusColors(Color(0xFFDBEAFE), Color(0xFF1D4ED8)), // Blue 100 / 700
    warning = StatusColors(Color(0xFFFEF3C7), Color(0xFFB45309)), // Amber 100 / 700
    critical = StatusColors(Color(0xFFFEE2E2), Color(0xFFB91C1C)), // Red 100 / 700
    neutral = StatusColors(Color(0xFFF1F5F9), Color(0xFF475569)), // Slate 100 / 600
    offline = StatusColors(Color(0xFFE2E8F0), Color(0xFF334155)), // Slate 200 / 700
)
