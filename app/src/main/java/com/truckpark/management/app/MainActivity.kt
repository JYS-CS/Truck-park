package com.truckpark.management.app

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truckpark.management.core.common.ThemeMode
import com.truckpark.management.core.common.resolveDark
import com.truckpark.management.core.designsystem.theme.TruckParkTheme
import com.truckpark.management.feature.auth.AdminSecuritySetupScreen
import com.truckpark.management.feature.auth.AuthUiState
import com.truckpark.management.feature.auth.AuthViewModel
import com.truckpark.management.feature.auth.LiquidMetalAuthScreen
import com.truckpark.management.feature.settings.ThemeViewModel
import com.truckpark.management.navigation.TruckParkAppRoot

class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels { ThemeViewModel.Factory }
    private val authViewModel: AuthViewModel by viewModels { AuthViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val initialMode = (application as? TruckParkApplication)?.container?.themeRepository?.getInitialThemeMode()
            ?: ThemeMode.SYSTEM
        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = initialMode.resolveDark(isSystemDark)
        val initialWindowBg = if (isDark) 0xFF05071E.toInt() else 0xFFF8FAFC.toInt()

        window.decorView.setBackgroundColor(initialWindowBg)
        window.setBackgroundDrawable(ColorDrawable(initialWindowBg))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val storedMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val mode = storedMode ?: initialMode

            var showSplash by rememberSaveable { mutableStateOf(true) }

            val darkTheme = mode.resolveDark(isSystemInDarkTheme())

            DisposableEffect(darkTheme) {
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
                val windowBg = if (darkTheme) 0xFF05071E.toInt() else 0xFFF8FAFC.toInt()
                window.decorView.setBackgroundColor(windowBg)
                onDispose { }
            }

            TruckParkTheme(darkTheme = darkTheme) {
                val authState by authViewModel.uiState.collectAsStateWithLifecycle()

                if (showSplash) {
                    com.truckpark.management.feature.splash.HibiscusAnimatedSplashScreen(
                        darkTheme = darkTheme,
                        onSplashFinished = { showSplash = false }
                    )
                } else {
                    when (val state = authState) {
                        is AuthUiState.AdminSecuritySetup -> {
                            AdminSecuritySetupScreen(
                                user = state.user,
                                initialPasswordChanged = state.passwordChanged,
                                initialEmailVerified = state.emailVerified,
                                onSetupComplete = { authViewModel.completeSecuritySetup() },
                                onSignOut = { authViewModel.signOut() },
                                viewModel = authViewModel,
                            )
                        }
                        is AuthUiState.Authenticated -> {
                            TruckParkAppRoot(
                                themeMode = mode,
                                onThemeModeSelected = themeViewModel::select,
                            )
                        }
                        else -> {
                            LiquidMetalAuthScreen(
                                viewModel = authViewModel,
                            )
                        }
                    }
                }
            }
        }
    }
}
