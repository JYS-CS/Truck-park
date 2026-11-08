package com.truckpark.management.feature.visits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truckpark.management.R
import com.truckpark.management.domain.models.ArrivalDraft
import com.truckpark.management.domain.models.ArrivalOutcome
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.domain.models.SyncState
import com.truckpark.management.domain.models.TruckVisit
import com.truckpark.management.domain.models.VisitType
import com.truckpark.management.domain.models.normalizePlate
import java.time.Duration
import java.time.Instant

@Composable
fun VisitsScreen(
    user: AuthUser?,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VisitsViewModel = viewModel(factory = VisitsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    var showArrivalDialog by remember { mutableStateOf(false) }
    var departureTargetVisit by remember { mutableStateOf<TruckVisit?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showArrivalDialog = true },
                containerColor = scheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.padding(bottom = 80.dp), // Clear bottom nav bar
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.btn_log_arrival),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.btn_log_arrival),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.visits_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        ),
                    )
                    Text(
                        text = stringResource(R.string.visits_subtitle),
                        style = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurfaceVariant),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Active On-Site Counter Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.primary.copy(alpha = if (dark) 0.18f else 0.12f))
                            .border(1.dp, scheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.lbl_active_on_site, uiState.openVisits.size),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                            ),
                        )
                    }

                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.nav_more),
                            tint = scheme.onSurface,
                        )
                    }
                }
            }

            // Sync Status & Offline Banner
            AnimatedVisibility(
                visible = uiState.pendingSyncCount > 0 || uiState.syncBannerMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                SyncStatusBanner(
                    pendingCount = uiState.pendingSyncCount,
                    isSyncing = uiState.isSyncing,
                    bannerMessage = uiState.syncBannerMessage,
                    onSyncClick = { viewModel.triggerSync() },
                    onDismissMessage = { viewModel.dismissBanner() },
                    dark = dark,
                )
            }

            // Search Bar & Filter Chips
            SearchAndFilterBar(
                searchQuery = uiState.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                selectedFilter = uiState.selectedTypeFilter,
                onSelectFilter = { viewModel.onTypeFilterSelected(it) },
                dark = dark,
            )

            // Active Visits List
            if (uiState.filteredVisits.isEmpty()) {
                EmptyVisitsState(
                    isFiltered = uiState.searchQuery.isNotBlank() || uiState.selectedTypeFilter != null,
                    dark = dark,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.filteredVisits, key = { it.id }) { visit ->
                        ActiveVisitCard(
                            visit = visit,
                            onDepartClick = { departureTargetVisit = visit },
                            dark = dark,
                        )
                    }
                }
            }
        }
    }

    // Gate Arrival Dialog Sheet
    if (showArrivalDialog) {
        GateArrivalDialog(
            openVisits = uiState.openVisits,
            plateSuggestions = uiState.plateSuggestions,
            onDismiss = {
                showArrivalDialog = false
                viewModel.clearArrivalOutcome()
            },
            onConfirm = { draft, allowDuplicate ->
                viewModel.recordArrival(draft, allowDuplicate)
                showArrivalDialog = false
            },
            dark = dark,
        )
    }

    // Gate Departure Dialog Sheet
    departureTargetVisit?.let { visit ->
        GateDepartureDialog(
            visit = visit,
            onDismiss = {
                departureTargetVisit = null
                viewModel.clearDepartureOutcome()
            },
            onConfirm = { notes ->
                viewModel.recordDeparture(visit.id, notes)
                departureTargetVisit = null
            },
            dark = dark,
        )
    }
}

// -----------------------------------------------------------------------------
// SYNC STATUS BANNER
// -----------------------------------------------------------------------------

@Composable
private fun SyncStatusBanner(
    pendingCount: Int,
    isSyncing: Boolean,
    bannerMessage: String?,
    onSyncClick: () -> Unit,
    onDismissMessage: () -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val amberAccent = Color(0xFFF59E0B)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (pendingCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(amberAccent.copy(alpha = if (dark) 0.16f else 0.12f))
                    .border(1.dp, amberAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = amberAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.lbl_pending_sync, pendingCount),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = scheme.onSurface,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(amberAccent)
                            .clickable(enabled = !isSyncing, onClick = onSyncClick)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = stringResource(R.string.btn_sync_now),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (bannerMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.4f else 0.6f))
                    .border(1.dp, scheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = bannerMessage,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable(onClick = onDismissMessage),
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// SEARCH AND FILTER BAR
// -----------------------------------------------------------------------------

@Composable
private fun SearchAndFilterBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    selectedFilter: VisitType?,
    onSelectFilter: (VisitType?) -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Frosted search input box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.5f))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.hint_search_visits),
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        inner()
                    },
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onQueryChange("") },
                    )
                }
            }
        }

        // Horizontal chips for visit types
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            item {
                FilterTypeChip(
                    label = stringResource(R.string.filter_all),
                    isSelected = selectedFilter == null,
                    onClick = { onSelectFilter(null) },
                    dark = dark,
                )
            }
            items(VisitType.entries.toTypedArray()) { type ->
                FilterTypeChip(
                    label = type.label,
                    isSelected = selectedFilter == type,
                    onClick = { onSelectFilter(type) },
                    dark = dark,
                )
            }
        }
    }
}

@Composable
private fun FilterTypeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) scheme.primary
                else scheme.surfaceVariant.copy(alpha = if (dark) 0.3f else 0.45f),
            )
            .border(
                1.dp,
                if (isSelected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp,
            ),
            color = if (isSelected) Color.White else scheme.onSurface,
        )
    }
}

// -----------------------------------------------------------------------------
// ACTIVE TRUCK VISIT CARD
// -----------------------------------------------------------------------------

@Composable
private fun ActiveVisitCard(
    visit: TruckVisit,
    onDepartClick: () -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val typeColor = when (visit.visitType) {
        VisitType.Quick -> Color(0xFF10B981)
        VisitType.Overnight -> Color(0xFF6366F1)
        VisitType.Transit -> Color(0xFF8B5CF6)
        VisitType.Loading -> Color(0xFFF59E0B)
        VisitType.Other -> scheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.5f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Row 1: Plate Badge & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // License Plate Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(scheme.surface.copy(alpha = 0.8f))
                            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "ZM",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = scheme.primary,
                                ),
                            )
                            Text(
                                text = visit.plateNumber,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp,
                                ),
                                color = scheme.onSurface,
                            )
                        }
                    }

                    // Visit Type Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(typeColor.copy(alpha = 0.16f))
                            .border(1.dp, typeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = visit.visitType.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                            ),
                            color = typeColor,
                        )
                    }
                }

                // Sync status indicator
                SyncBadge(state = visit.syncState)
            }

            // Row 2: Driver details
            if (!visit.driverName.isNullOrBlank() || !visit.driverPhone.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    visit.driverName?.let { name ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = scheme.onSurface,
                            )
                        }
                    }

                    visit.driverPhone?.let { phone ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = phone,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Row 3: Notes (if present)
            visit.arrivalNotes?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = scheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.15f))

            // Row 4: Elapsed Duration & Log Departure Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = formatElapsedDuration(visit.arrivedAt),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = scheme.onSurfaceVariant,
                    )
                    visit.reference?.let { ref ->
                        Text(
                            text = "· $ref",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }

                // Log Departure Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onDepartClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.btn_log_departure),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncBadge(state: SyncState) {
    val scheme = MaterialTheme.colorScheme
    val (label, color, icon) = when (state) {
        SyncState.Synced -> Triple("Synced", Color(0xFF10B981), Icons.Default.CloudDone)
        SyncState.Pending -> Triple("Pending sync", Color(0xFFF59E0B), Icons.Default.CloudUpload)
        SyncState.Conflict -> Triple("Conflict", scheme.error, Icons.Default.Warning)
        SyncState.Failed -> Triple("Failed", scheme.error, Icons.Default.Close)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = color,
        )
    }
}

// -----------------------------------------------------------------------------
// GATE ARRIVAL CHECK-IN DIALOG
// -----------------------------------------------------------------------------

@Composable
private fun GateArrivalDialog(
    openVisits: List<TruckVisit>,
    plateSuggestions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (draft: ArrivalDraft, allowDuplicate: Boolean) -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    var plateInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(VisitType.Quick) }
    var driverNameInput by remember { mutableStateOf("") }
    var driverPhoneInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var allowDuplicateOverride by remember { mutableStateOf(false) }

    val cleanPlate = normalizePlate(plateInput)
    val existingOpenVisit = remember(cleanPlate, openVisits) {
        if (cleanPlate.isEmpty()) null
        else openVisits.find { it.plateNumber == cleanPlate }
    }

    // Phone validation for Zambia (+260 + 9 digits = 12 digits)
    val digitsOnlyPhone = driverPhoneInput.filter { it.isDigit() }
    val isPhoneValid = digitsOnlyPhone.length == 9 // 9 subscriber digits

    val canSave = cleanPlate.isNotBlank() && (existingOpenVisit == null || allowDuplicateOverride)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = scheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.dialog_arrival_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss)
                            .padding(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))

                // Plate Input
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.lbl_plate_number),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        BasicTextField(
                            value = plateInput,
                            onValueChange = { plateInput = it.take(20).uppercase() },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = scheme.onSurface,
                            ),
                            cursorBrush = SolidColor(scheme.primary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (plateInput.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.hint_plate_number),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                inner()
                            },
                        )
                    }

                    // Known plate suggestions
                    if (plateSuggestions.isNotEmpty() && plateInput.length >= 2) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            items(plateSuggestions) { plate ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(scheme.primary.copy(alpha = 0.12f))
                                        .clickable { plateInput = plate }
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = plate,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                        ),
                                        color = scheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                // Duplicate Open Visit Warning Box
                if (existingOpenVisit != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.14f))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = stringResource(R.string.warn_duplicate_open_title),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF59E0B),
                                    ),
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.warn_duplicate_open_msg,
                                    existingOpenVisit.plateNumber,
                                    existingOpenVisit.reference ?: "Local",
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = scheme.onSurface,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.clickable {
                                    allowDuplicateOverride = !allowDuplicateOverride
                                },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (allowDuplicateOverride) Color(0xFFF59E0B) else Color.Transparent)
                                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (allowDuplicateOverride) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.lbl_override_duplicate),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = Color(0xFFF59E0B),
                                )
                            }
                        }
                    }
                }

                // Visit Type
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.lbl_visit_type),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        VisitType.entries.forEach { type ->
                            val isSelected = selectedType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) scheme.primary
                                        else scheme.surfaceVariant.copy(alpha = 0.5f),
                                    )
                                    .clickable { selectedType = type }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = type.label.split(" ").first(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 10.sp,
                                    ),
                                    color = if (isSelected) Color.White else scheme.onSurface,
                                )
                            }
                        }
                    }
                }

                // Driver Details (Optional)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Driver Name
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.lbl_driver_name),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = scheme.onSurface,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            BasicTextField(
                                value = driverNameInput,
                                onValueChange = { driverNameInput = it.take(100) },
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurface),
                                cursorBrush = SolidColor(scheme.primary),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (driverNameInput.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.hint_driver_name),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    inner()
                                },
                            )
                        }
                    }

                    // Driver Phone (Zambia +260 validation)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Driver Phone (+260)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = scheme.onSurface,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "+260",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                    ),
                                    color = scheme.onSurfaceVariant,
                                )
                                BasicTextField(
                                    value = driverPhoneInput,
                                    onValueChange = {
                                        val digits = it.filter { ch -> ch.isDigit() }.take(9)
                                        driverPhoneInput = digits
                                    },
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurface),
                                    cursorBrush = SolidColor(scheme.primary),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        if (driverPhoneInput.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.hint_driver_phone),
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            )
                                        }
                                        inner()
                                    },
                                )
                                if (isPhoneValid) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Valid",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Notes (Optional)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.lbl_arrival_notes),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        BasicTextField(
                            value = notesInput,
                            onValueChange = { notesInput = it.take(300) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurface),
                            cursorBrush = SolidColor(scheme.primary),
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (notesInput.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.hint_arrival_notes),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }

                // Actions: Cancel / Confirm Arrival
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.btn_cancel),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = scheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (canSave) scheme.primary else scheme.outlineVariant.copy(alpha = 0.3f))
                            .clickable(enabled = canSave) {
                                val fullPhone = if (digitsOnlyPhone.isNotEmpty()) "+260$digitsOnlyPhone" else null
                                val draft = ArrivalDraft(
                                    plateNumber = cleanPlate,
                                    visitType = selectedType,
                                    driverName = driverNameInput.ifBlank { null },
                                    driverPhone = fullPhone,
                                    notes = notesInput.ifBlank { null },
                                )
                                onConfirm(draft, allowDuplicateOverride)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.btn_confirm_arrival),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (canSave) Color.White else scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// GATE DEPARTURE CONFIRMATION DIALOG
// -----------------------------------------------------------------------------

@Composable
private fun GateDepartureDialog(
    visit: TruckVisit,
    onDismiss: () -> Unit,
    onConfirm: (notes: String?) -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    var departureNotes by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = scheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.dialog_departure_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss)
                            .padding(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.2f))

                // Truck Summary Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = visit.plateNumber,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = scheme.onSurface,
                            )
                            Text(
                                text = visit.visitType.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = scheme.primary,
                            )
                        }

                        visit.reference?.let { ref ->
                            Text(
                                text = "Reference: #$ref",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = scheme.onSurfaceVariant,
                            )
                        }

                        Text(
                            text = "Total stay: ${formatElapsedDuration(visit.arrivedAt)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                            ),
                        )
                    }
                }

                // Departure Notes Input
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.lbl_departure_notes),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        BasicTextField(
                            value = departureNotes,
                            onValueChange = { departureNotes = it.take(300) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurface),
                            cursorBrush = SolidColor(scheme.primary),
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (departureNotes.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.hint_departure_notes),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.btn_cancel),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = scheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEF4444))
                            .clickable {
                                onConfirm(departureNotes.ifBlank { null })
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.btn_confirm_departure),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// EMPTY STATE
// -----------------------------------------------------------------------------

@Composable
private fun EmptyVisitsState(
    isFiltered: Boolean,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = if (dark) 0.15f else 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = if (isFiltered) "No Trucks Match Filter" else stringResource(R.string.empty_visits_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = scheme.onSurface,
            )
            Text(
                text = if (isFiltered) "Try adjusting your search query or visit type filter."
                else stringResource(R.string.empty_visits_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

// -----------------------------------------------------------------------------
// HELPERS
// -----------------------------------------------------------------------------

private fun formatElapsedDuration(arrivedAt: Instant): String {
    val duration = Duration.between(arrivedAt, Instant.now())
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return when {
        hours <= 0 && minutes <= 1 -> "Arrived just now"
        hours <= 0 -> "Arrived ${minutes}m ago"
        hours < 24 -> "Arrived ${hours}h ${minutes}m ago"
        else -> {
            val days = hours / 24
            val remHours = hours % 24
            if (remHours > 0) "Arrived ${days}d ${remHours}h ago" else "Arrived ${days}d ago"
        }
    }
}

