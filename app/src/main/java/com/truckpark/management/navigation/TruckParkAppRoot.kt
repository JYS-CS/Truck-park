package com.truckpark.management.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.truckpark.management.R
import com.truckpark.management.core.common.ThemeMode
import com.truckpark.management.core.designsystem.components.AppBackground
import com.truckpark.management.feature.placeholder.PlannedFeatureScreen
import com.truckpark.management.feature.settings.MoreScreen

/**
 * Adaptive shell: NavigationSuiteScaffold shows a bottom bar on phones and a navigation
 * rail on wider windows. Back-stack state is saved per tab.
 */
@Composable
fun TruckParkAppRoot(themeMode: ThemeMode, onThemeModeSelected: (ThemeMode) -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    AppBackground {
        NavigationSuiteScaffold(
            containerColor = Color.Transparent,
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            navigationSuiteItems = {
                TopLevelDestination.entries.forEach { destination ->
                    item(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                        alwaysShowLabel = true,
                    )
                }
            },
        ) {
            Box(Modifier.statusBarsPadding()) {
                NavHost(navController = navController, startDestination = TopLevelDestination.Home.route) {
                    composable(TopLevelDestination.Home.route) {
                        PlannedFeatureScreen(
                            title = stringResource(R.string.nav_home),
                            summary = stringResource(R.string.home_summary),
                            milestone = stringResource(R.string.home_milestone),
                        )
                    }
                    composable(TopLevelDestination.Visits.route) {
                        PlannedFeatureScreen(
                            title = stringResource(R.string.nav_visits),
                            summary = stringResource(R.string.visits_summary),
                            milestone = stringResource(R.string.visits_milestone),
                        )
                    }
                    composable(TopLevelDestination.Registers.route) {
                        PlannedFeatureScreen(
                            title = stringResource(R.string.nav_registers),
                            summary = stringResource(R.string.registers_summary),
                            milestone = stringResource(R.string.registers_milestone),
                        )
                    }
                    composable(TopLevelDestination.Reports.route) {
                        PlannedFeatureScreen(
                            title = stringResource(R.string.nav_reports),
                            summary = stringResource(R.string.reports_summary),
                            milestone = stringResource(R.string.reports_milestone),
                        )
                    }
                    composable(TopLevelDestination.More.route) {
                        MoreScreen(themeMode, onThemeModeSelected)
                    }
                }
            }
        }
    }
}
