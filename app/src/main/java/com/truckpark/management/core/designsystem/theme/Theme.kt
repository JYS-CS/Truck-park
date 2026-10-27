package com.truckpark.management.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalTruckParkColors = staticCompositionLocalOf { LightExtendedColors }

/** Access to the extra colours: `TruckParkExtras.colors.glassFill`. */
object TruckParkExtras {
    val colors: TruckParkColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTruckParkColors.current
}

/**
 * Root theme. [darkTheme] is resolved by the caller from the saved [com.truckpark.management.core.common.ThemeMode].
 * The theme lives entirely in composition, so switching it never recreates the activity
 * and cannot discard remembered or saved UI state.
 */
@Composable
fun TruckParkTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTruckParkColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = TruckParkTypography,
            content = content,
        )
    }
}
