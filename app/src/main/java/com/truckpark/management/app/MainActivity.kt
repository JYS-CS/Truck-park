package com.truckpark.management.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truckpark.management.core.common.resolveDark
import com.truckpark.management.core.designsystem.theme.TruckParkTheme
import com.truckpark.management.feature.settings.ThemeViewModel
import com.truckpark.management.navigation.TruckParkAppRoot

class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels { ThemeViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val storedMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val mode = storedMode
            // Render nothing for the first frame(s) until the saved preference is loaded.
            if (mode != null) {
                val darkTheme = mode.resolveDark(isSystemInDarkTheme())

                DisposableEffect(darkTheme) {
                    // Keep status/navigation bar icon contrast correct for the *chosen* theme,
                    // which may differ from the system theme.
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT,
                        ) { darkTheme },
                        navigationBarStyle = SystemBarStyle.auto(
                            AndroidColor.argb(0xe6, 0xFF, 0xFF, 0xFF),
                            AndroidColor.argb(0x80, 0x1b, 0x1b, 0x1b),
                        ) { darkTheme },
                    )
                    onDispose { }
                }

                TruckParkTheme(darkTheme = darkTheme) {
                    TruckParkAppRoot(
                        themeMode = mode,
                        onThemeModeSelected = themeViewModel::select,
                    )
                }
            }
        }
    }
}
