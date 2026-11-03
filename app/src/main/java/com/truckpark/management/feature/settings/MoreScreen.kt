package com.truckpark.management.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.truckpark.management.R
import com.truckpark.management.core.common.ThemeMode
import com.truckpark.management.core.designsystem.components.GlassCard
import com.truckpark.management.core.designsystem.icons.TruckParkIcons
import com.truckpark.management.domain.models.AuthUser

@Composable
fun MoreScreen(themeMode: ThemeMode, onThemeModeSelected: (ThemeMode) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.more_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        AppearanceCard(themeMode, onThemeModeSelected)
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AppearanceCard(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    ThemeModeSelector(selected = selected, onSelected = onSelected)
}

@Composable
fun ThemeModeSelector(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeOption(
            mode = ThemeMode.SYSTEM,
            title = "System Default",
            subtitle = "Follow device light or dark mode",
            icon = TruckParkIcons.ThemeSystem,
            accentColor = Color(0xFF6366F1), // Indigo
        ),
        ThemeOption(
            mode = ThemeMode.LIGHT,
            title = "Light Theme",
            subtitle = "Crisp, clean high-contrast daytime mode",
            icon = TruckParkIcons.ThemeLight,
            accentColor = Color(0xFFEA580C), // Warm Amber-Orange
        ),
        ThemeOption(
            mode = ThemeMode.DARK,
            title = "Dark Theme",
            subtitle = "Sleek, battery-friendly midnight mode",
            icon = TruckParkIcons.ThemeDark,
            accentColor = Color(0xFF818CF8), // Sky/Lavender
        ),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.mode == selected
            val animatedBorderColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) option.accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                label = "border_${option.mode}",
            )
            val animatedBgColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) option.accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                label = "bg_${option.mode}",
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 1.6.dp else 1.dp,
                        color = animatedBorderColor,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    )
                    .background(animatedBgColor, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelected(option.mode) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Frosted Glass Icon Badge with subtle accent glow
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = option.accentColor.copy(alpha = if (isSelected) 0.20f else 0.10f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        )
                        .border(
                            width = 1.dp,
                            color = option.accentColor.copy(alpha = if (isSelected) 0.40f else 0.20f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = option.icon,
                        contentDescription = option.title,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp),
                    )
                }

                // Title and description
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = option.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) option.accentColor else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = option.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    )
                }

                // Selection Radio Indicator
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(
                            width = 2.dp,
                            color = if (isSelected) option.accentColor else MaterialTheme.colorScheme.outlineVariant,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .background(
                                    option.accentColor,
                                    androidx.compose.foundation.shape.CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

private data class ThemeOption(
    val mode: ThemeMode,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accentColor: Color,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    user: AuthUser? = null,
    onRefreshRole: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    if (visible) {
        val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            dragHandle = {
                androidx.compose.material3.BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                )
            },
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .padding(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (user != null) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = user.fullName.ifBlank { "Operator" },
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = user.primaryRole,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp,
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                        Text(
                                            text = user.email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .clickable {
                                            onRefreshRole()
                                            onDismissRequest()
                                        }
                                        .padding(vertical = 10.dp, horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Sync Role & Permissions",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    // Theme Mode Selection
                    ThemeModeSelector(
                        selected = themeMode,
                        onSelected = onThemeModeSelected,
                    )

                    // About Card with clear, high-contrast text
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.about_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.about_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                        )
                    }

                    // Sign Out Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                            .border(
                                width = 1.dp,
                                color = Color(0xFFEF4444).copy(alpha = 0.45f),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .clickable(onClick = onSignOut)
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFEF4444),
                        )
                    }

                    // Footer version note with high-contrast text
                    Text(
                        text = "Truck Park v1.0.0 (Milestone 1)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}
