package com.truckpark.management.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.truckpark.management.R
import com.truckpark.management.core.designsystem.icons.TruckParkIcons

/** The five primary sections required by the specification. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home("home", R.string.nav_home, TruckParkIcons.Home),
    Visits("visits", R.string.nav_visits, TruckParkIcons.Truck),
    Registers("registers", R.string.nav_registers, TruckParkIcons.Registers),
    Reports("reports", R.string.nav_reports, TruckParkIcons.Reports),
    More("more", R.string.nav_more, TruckParkIcons.More),
}
