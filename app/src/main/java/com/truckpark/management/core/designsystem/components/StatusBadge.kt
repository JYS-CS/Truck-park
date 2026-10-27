package com.truckpark.management.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.truckpark.management.core.designsystem.icons.TruckParkIcons
import com.truckpark.management.core.designsystem.theme.StatusColors
import com.truckpark.management.core.designsystem.theme.TruckParkExtras

enum class StatusKind(val icon: ImageVector) {
    Success(TruckParkIcons.Success),
    Info(TruckParkIcons.Info),
    Warning(TruckParkIcons.Warning),
    Critical(TruckParkIcons.Critical),
    Neutral(TruckParkIcons.Neutral),
    Offline(TruckParkIcons.Offline),
}

/** A status is always communicated by colour + icon + text label together, never colour alone. */
@Composable
fun StatusBadge(kind: StatusKind, label: String, modifier: Modifier = Modifier) {
    val palette = TruckParkExtras.colors
    val colors: StatusColors = when (kind) {
        StatusKind.Success -> palette.success
        StatusKind.Info -> palette.info
        StatusKind.Warning -> palette.warning
        StatusKind.Critical -> palette.critical
        StatusKind.Neutral -> palette.neutral
        StatusKind.Offline -> palette.offline
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colors.container)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kind.icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = colors.content, style = MaterialTheme.typography.labelLarge)
    }
}
