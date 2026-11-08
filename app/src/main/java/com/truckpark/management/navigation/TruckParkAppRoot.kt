package com.truckpark.management.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.feature.auth.AuthViewModel
import com.truckpark.management.feature.operations.OperationsScreen
import com.truckpark.management.feature.settings.MoreOptionsBottomSheet
import com.truckpark.management.feature.visits.VisitsScreen

/**
 * Root container with floating iOS-style liquid glass navigation bar
 * and top-right hamburger options drawer.
 * Navigation tabs and workspace cards dynamically adapt to the signed-in user's role.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TruckParkAppRoot(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    user: AuthUser? = null,
    authViewModel: AuthViewModel? = null,
    onSignOut: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var selectedRoute by remember { mutableStateOf(TopLevelDestination.Home.route) }
    var moreSheetOpen by remember { mutableStateOf(false) }
    var showBottomNavByScreen by remember { mutableStateOf(true) }
    val effectiveRoute = currentDestination?.route ?: selectedRoute

    // Hide bottom navigation bar when soft keyboard (IME) is visible or when a sub-screen flow requests it
    val isImeVisible = WindowInsets.isImeVisible
    val showBottomBar = showBottomNavByScreen && !isImeVisible

    val navBottomPadding by animateDpAsState(
        targetValue = if (showBottomBar) 80.dp else 0.dp,
        label = "navBottomPadding",
    )

    // Filter navigation bar tabs dynamically based on the user's assigned role
    val roleDestinations = remember(user?.primaryRole) {
        when (user?.primaryRole) {
            "ADMIN" -> TopLevelDestination.entries
            "SUPERVISOR" -> listOf(
                TopLevelDestination.Home,
                TopLevelDestination.Visits,
                TopLevelDestination.Registers,
            )
            else -> listOf(
                TopLevelDestination.Home,
                TopLevelDestination.Visits,
            )
        }
    }

    LaunchedEffect(roleDestinations, effectiveRoute) {
        if (roleDestinations.none { it.route == effectiveRoute }) {
            selectedRoute = TopLevelDestination.Home.route
            navController.navigate(TopLevelDestination.Home.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
    }

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
                    .padding(bottom = navBottomPadding),
            ) {
                NavHost(navController = navController, startDestination = TopLevelDestination.Home.route) {
                    composable(TopLevelDestination.Home.route) {
                        OperationsScreen(
                            title = stringResource(R.string.nav_home),
                            summary = stringResource(R.string.home_summary),
                            milestone = stringResource(R.string.home_milestone),
                            icon = TopLevelDestination.Home.icon,
                            user = user,
                            authViewModel = authViewModel,
                            isHomeTab = true,
                            onMenuClick = { moreSheetOpen = true },
                        )
                    }
                    composable(TopLevelDestination.Visits.route) {
                        VisitsScreen(
                            user = user,
                            onMenuClick = { moreSheetOpen = true },
                            onToggleBottomNav = { showBottomNavByScreen = it },
                        )
                    }
                    composable(TopLevelDestination.Registers.route) {
                        OperationsScreen(
                            title = stringResource(R.string.nav_registers),
                            summary = stringResource(R.string.registers_summary),
                            milestone = stringResource(R.string.registers_milestone),
                            icon = TopLevelDestination.Registers.icon,
                            user = user,
                            authViewModel = authViewModel,
                            onMenuClick = { moreSheetOpen = true },
                        )
                    }
                    composable(TopLevelDestination.Reports.route) {
                        OperationsScreen(
                            title = stringResource(R.string.nav_reports),
                            summary = stringResource(R.string.reports_summary),
                            milestone = stringResource(R.string.reports_milestone),
                            icon = TopLevelDestination.Reports.icon,
                            user = user,
                            authViewModel = authViewModel,
                            onMenuClick = { moreSheetOpen = true },
                        )
                    }
                }
            }

            // Floating iOS Liquid Glass Navigation Bar (hidden when keyboard is open or in sub-flows)
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                LiquidGlassNavigationBar(
                    destinations = roleDestinations,
                    currentRoute = effectiveRoute,
                    onDestinationSelected = { destination ->
                        selectedRoute = destination.route
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            // Top-right Hamburger Menu Slide-over Drawer / BottomSheet matching HTML
            MoreOptionsBottomSheet(
                visible = moreSheetOpen,
                onDismissRequest = { moreSheetOpen = false },
                themeMode = themeMode,
                onThemeModeSelected = onThemeModeSelected,
                user = user,
                onRefreshRole = {
                    authViewModel?.checkCurrentSession()
                },
                onSignOut = {
                    moreSheetOpen = false
                    onSignOut()
                },
            )
        }
    }
}
