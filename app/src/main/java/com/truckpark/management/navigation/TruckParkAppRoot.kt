package com.truckpark.management.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.truckpark.management.R
import com.truckpark.management.core.common.ThemeMode
import com.truckpark.management.core.designsystem.components.AppBackground
import com.truckpark.management.core.designsystem.components.LiquidGlassNavigationBar
import com.truckpark.management.feature.placeholder.PlannedFeatureScreen
import com.truckpark.management.feature.settings.MoreOptionsBottomSheet

/**
 * Root container with floating iOS-style liquid glass navigation bar
 * and top-right hamburger options drawer.
 */
@Composable
fun TruckParkAppRoot(themeMode: ThemeMode, onThemeModeSelected: (ThemeMode) -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var selectedRoute by remember { mutableStateOf(TopLevelDestination.Home.route) }
    var moreSheetOpen by remember { mutableStateOf(false) }
    val effectiveRoute = currentDestination?.route ?: selectedRoute

    AppBackground {
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            // Main content area with bottom padding calculated dynamically so floating liquid bar never overlaps content
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
            ) {
                NavHost(navController = navController, startDestination = TopLevelDestination.Home.route) {
                    composable(TopLevelDestination.Home.route) {
                        PlannedFeatureScreen(
                            title = stringResource(R.string.nav_home),
                            summary = stringResource(R.string.home_summary),
                            milestone = stringResource(R.string.home_milestone),
                            icon = TopLevelDestination.Home.icon,
                            onMenuClick = { moreSheetOpen = true },
                        )
                    }
                    composable(TopLevelDestination.Visits.route) {
                        PlannedFeatureScreen(
                            title = stringResource(R.string.nav_visits),
                            summary = stringResource(R.string.visits_summary),
                            milestone = stringResource(R.string.visits_milestone),
                            icon = TopLevelDestination.Visits.icon,
                            onMenuClick = { moreSheetOpen = true },
                        )
                    }
                    composable(TopLevelDestination.Registers.route) {
                        PlannedFeatureScreen(
                            title = stringResource(R.string.nav_registers),
                            summary = stringResource(R.string.registers_summary),
                            milestone = stringResource(R.string.registers_milestone),
                            icon = TopLevelDestination.Registers.icon,
                            onMenuClick = { moreSheetOpen = true },
                        )
                    }
                    composable(TopLevelDestination.Reports.route) {
                        PlannedFeatureScreen(
                            title = stringResource(R.string.nav_reports),
                            summary = stringResource(R.string.reports_summary),
                            milestone = stringResource(R.string.reports_milestone),
                            icon = TopLevelDestination.Reports.icon,
                            onMenuClick = { moreSheetOpen = true },
                        )
                    }
                }
            }

            // Floating iOS Liquid Glass Navigation Bar
            LiquidGlassNavigationBar(
                destinations = TopLevelDestination.entries,
                currentRoute = effectiveRoute,
                onDestinationSelected = { destination ->
                    selectedRoute = destination.route
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter),
            )

            // Top-right Hamburger Menu Slide-over Drawer / BottomSheet matching HTML
            MoreOptionsBottomSheet(
                visible = moreSheetOpen,
                onDismissRequest = { moreSheetOpen = false },
                themeMode = themeMode,
                onThemeModeSelected = onThemeModeSelected,
            )
        }
    }
}
