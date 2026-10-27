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

// ---------- Dark: midnight navy, deep blue, cyan and indigo ----------
val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF4FD8EB),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF9CF0FF),
    secondary = Color(0xFFB8C3FF),
    onSecondary = Color(0xFF1F2D78),
    secondaryContainer = Color(0xFF36448F),
    onSecondaryContainer = Color(0xFFDDE1FF),
    tertiary = Color(0xFF8FB4FF),
    onTertiary = Color(0xFF002F69),
    tertiaryContainer = Color(0xFF0B4590),
    onTertiaryContainer = Color(0xFFD8E2FF),
    background = Color(0xFF070D1F),
    onBackground = Color(0xFFE3E8F7),
    surface = Color(0xFF0B1330),
    onSurface = Color(0xFFE3E8F7),
    surfaceVariant = Color(0xFF1A2447),
    onSurfaceVariant = Color(0xFFB4BDD6),
    surfaceTint = Color(0xFF4FD8EB),
    surfaceContainerLowest = Color(0xFF060A1A),
    surfaceContainerLow = Color(0xFF0D1535),
    surfaceContainer = Color(0xFF111B3E),
    surfaceContainerHigh = Color(0xFF172248),
    surfaceContainerHighest = Color(0xFF1E2A55),
    outline = Color(0xFF7A85A8),
    outlineVariant = Color(0xFF2A3560),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

val DarkExtendedColors = TruckParkColors(
    backgroundTop = Color(0xFF0A1433),
    backgroundBottom = Color(0xFF050A18),
    glassFill = Color(0xFF16214A).copy(alpha = 0.78f),
    glassHighlight = Color(0xFF4FD8EB).copy(alpha = 0.10f),
    glassBorder = Color(0xFF8FA6E8).copy(alpha = 0.28f),
    success = StatusColors(Color(0xFF0F3B28), Color(0xFF8FE8B6)),
    info = StatusColors(Color(0xFF12306B), Color(0xFFB3CDFF)),
    warning = StatusColors(Color(0xFF4A3300), Color(0xFFFFD27A)),
    critical = StatusColors(Color(0xFF5E0E14), Color(0xFFFFB4AB)),
    neutral = StatusColors(Color(0xFF222C50), Color(0xFFC9D1E8)),
    offline = StatusColors(Color(0xFF2A2A5E), Color(0xFFCFCFFF)),
)

// ---------- Light: clean surfaces, blue-grey borders, cyan accents ----------
val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF006874),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF0FF),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF4A5AA8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE1FF),
    onSecondaryContainer = Color(0xFF00105C),
    tertiary = Color(0xFF2F5EA8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD8E2FF),
    onTertiaryContainer = Color(0xFF001A41),
    background = Color(0xFFF4F7FC),
    onBackground = Color(0xFF0E1528),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0E1528),
    surfaceVariant = Color(0xFFE3E8F3),
    onSurfaceVariant = Color(0xFF454E66),
    surfaceTint = Color(0xFF006874),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9FD),
    surfaceContainer = Color(0xFFEEF2F9),
    surfaceContainerHigh = Color(0xFFE8EDF6),
    surfaceContainerHighest = Color(0xFFE1E7F2),
    outline = Color(0xFF737C96),
    outlineVariant = Color(0xFFC9D1E3),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

val LightExtendedColors = TruckParkColors(
    backgroundTop = Color(0xFFF4F8FF),
    backgroundBottom = Color(0xFFE9EFF9),
    glassFill = Color(0xFFFFFFFF).copy(alpha = 0.88f),
    glassHighlight = Color(0xFF00B8D4).copy(alpha = 0.07f),
    glassBorder = Color(0xFF9AA8C7).copy(alpha = 0.55f),
    success = StatusColors(Color(0xFFD6F5E3), Color(0xFF0B5A33)),
    info = StatusColors(Color(0xFFD8E6FF), Color(0xFF0F3E8A)),
    warning = StatusColors(Color(0xFFFFE9B8), Color(0xFF5C3B00)),
    critical = StatusColors(Color(0xFFFFDAD6), Color(0xFF8C0009)),
    neutral = StatusColors(Color(0xFFE3E8F3), Color(0xFF2B3452)),
    offline = StatusColors(Color(0xFFE3E2FB), Color(0xFF2F2F6B)),
)
