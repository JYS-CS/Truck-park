package com.truckpark.management.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.truckpark.management.R
import com.truckpark.management.core.designsystem.icons.TruckParkIcons

/** The 4 primary sections shown on the navigation bar, matching the HTML mockup. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home("home", R.string.nav_home, TruckParkIcons.Home),
    Visits("visits", R.string.nav_visits, TruckParkIcons.Truck),
    Registers("registers", R.string.nav_registers, TruckParkIcons.Registers),
    Reports("reports", R.string.nav_reports, TruckParkIcons.Reports),
}
