package com.truckpark.management.feature.visits

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truckpark.management.R
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.core.designsystem.components.GlassCard
import com.truckpark.management.core.designsystem.theme.TruckParkExtras
import com.truckpark.management.data.local.TransporterStore
import com.truckpark.management.domain.models.ArrivalDraft
import com.truckpark.management.domain.models.ArrivalOutcome
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.domain.models.CargoItem
import com.truckpark.management.domain.models.CustomVisitField
import com.truckpark.management.domain.models.PlateInfo
import com.truckpark.management.domain.models.SealCondition
import com.truckpark.management.domain.models.SyncAlertBanner
import com.truckpark.management.domain.models.SyncAlertSeverity
import com.truckpark.management.domain.models.SyncState
import com.truckpark.management.domain.models.TruckVisit
import com.truckpark.management.domain.models.VisitCorrectionDraft
import com.truckpark.management.domain.models.VisitCorrectionRecord
import com.truckpark.management.domain.models.VisitFieldCategory
import com.truckpark.management.domain.models.VisitSeal
import com.truckpark.management.domain.models.VisitStatus
import com.truckpark.management.domain.models.VisitType
import com.truckpark.management.domain.models.cleanDisplayNotes
import com.truckpark.management.domain.models.countryFlagForCode
import com.truckpark.management.domain.models.countryNameForCode
import com.truckpark.management.domain.models.encodeCustomFieldsTags
import com.truckpark.management.domain.models.extractPlateInfo
import com.truckpark.management.domain.models.normalizePlate
import com.truckpark.management.domain.models.sanitizeTagPart
import com.truckpark.management.feature.ocr.OcrScanMode
import com.truckpark.management.feature.ocr.PlateScannerDialog
import com.truckpark.management.feature.ocr.PlateTextAnalyzer
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

internal val CapsuleShape = RoundedCornerShape(999.dp)
private val PlateShape = RoundedCornerShape(8.dp)

/**
 * Screen modes within Visits destination:
 *  - [List]: Active yard fleet overview and search registry.
 *  - [Arrival]: Dedicated full-page check-in flow.
 *  - [Departure]: Dedicated full-page check-out flow.
 */
private sealed interface VisitsScreenMode {
    data object List : VisitsScreenMode
    data object Arrival : VisitsScreenMode
    data class Departure(
        val visitId: String,
        val fallbackVisit: TruckVisit,
        val focusDeparture: Boolean = false,
    ) : VisitsScreenMode
}

@Composable
fun VisitsScreen(
    user: AuthUser?,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleBottomNav: (Boolean) -> Unit = {},
    viewModel: VisitsViewModel = viewModel(factory = VisitsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    val canArrive = user?.can("visit.arrive") ?: true
    val canDepart = user?.can("visit.depart") ?: true
    val canCorrectCritical = user?.can("visit.correct_critical") ?: (user?.isAdmin == true || user?.isSupervisor == true)
    val canEditOpen = canArrive || canCorrectCritical
    val canResolveConflicts = canCorrectCritical || user?.isAdmin == true || user?.isSupervisor == true

    var screenMode by remember { mutableStateOf<VisitsScreenMode>(VisitsScreenMode.List) }
    var elapsedTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            elapsedTick++
        }
    }

    // Navigate back cleanly only once arrival or departure succeeds
    LaunchedEffect(uiState.arrivalOutcome, screenMode) {
        if (screenMode is VisitsScreenMode.Arrival && uiState.arrivalOutcome is ArrivalOutcome.Saved) {
            viewModel.clearArrivalOutcome()
            screenMode = VisitsScreenMode.List
        }
    }
    LaunchedEffect(uiState.departureOutcome, screenMode) {
        if (screenMode is VisitsScreenMode.Departure && uiState.departureOutcome is AuthResult.Success) {
            viewModel.clearDepartureOutcome()
            screenMode = VisitsScreenMode.List
        }
    }

    // Toggle bottom navigation bar visibility based on active sub-flow
    LaunchedEffect(screenMode) {
        onToggleBottomNav(screenMode == VisitsScreenMode.List)
    }

    // Intercept back navigation when in full-page sub-flows
    BackHandler(enabled = screenMode != VisitsScreenMode.List) {
        viewModel.clearArrivalOutcome()
        viewModel.clearDepartureOutcome()
        viewModel.clearCorrectionOutcome()
        screenMode = VisitsScreenMode.List
    }

    AnimatedContent(
        targetState = screenMode,
        transitionSpec = {
            if (targetState == VisitsScreenMode.List) {
                (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
            } else {
                (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
            }
        },
        label = "VisitsScreenNavigation",
        modifier = modifier.fillMaxSize(),
    ) { mode ->
        when (mode) {
            is VisitsScreenMode.List -> {
                VisitsListContent(
                    uiState = uiState,
                    elapsedTick = elapsedTick,
                    canArrive = canArrive,
                    canDepart = canDepart,
                    dark = dark,
                    onMenuClick = onMenuClick,
                    onLogArrivalClick = {
                        viewModel.clearArrivalOutcome()
                        screenMode = VisitsScreenMode.Arrival
                    },
                    onVisitClick = { visit ->
                        viewModel.clearDepartureOutcome()
                        viewModel.clearCorrectionOutcome()
                        screenMode = VisitsScreenMode.Departure(visit.id, visit, focusDeparture = false)
                    },
                    onQuickDepartClick = { visit ->
                        viewModel.clearDepartureOutcome()
                        viewModel.clearCorrectionOutcome()
                        screenMode = VisitsScreenMode.Departure(visit.id, visit, focusDeparture = true)
                    },
                    onShowCompletedChanged = { viewModel.onShowCompletedChanged(it) },
                    onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onTypeFilterSelected = { viewModel.onTypeFilterSelected(it) },
                    onManualRetrySync = { viewModel.manualRetrySync() },
                    onPullToRefresh = {
                        elapsedTick++
                        viewModel.syncAndRefresh(silent = true)
                    },
                    onDismissBanner = { viewModel.dismissBanner() },
                    onReviewConflicts = {
                        uiState.conflictVisits.firstOrNull()?.let { conflict ->
                            viewModel.clearDepartureOutcome()
                            viewModel.clearCorrectionOutcome()
                            screenMode = VisitsScreenMode.Departure(conflict.id, conflict)
                        }
                    },
                )
            }
            is VisitsScreenMode.Arrival -> {
                val activeUserName = user?.fullName?.takeIf { it.isNotBlank() } ?: user?.email
                GateArrivalFullPage(
                    openVisits = uiState.openVisits,
                    plateSuggestions = uiState.plateSuggestions,
                    isSubmitting = uiState.isSubmittingArrival,
                    arrivalOutcome = uiState.arrivalOutcome,
                    currentUserName = activeUserName,
                    onPlateQueryChange = { viewModel.onPlateInputChanged(it) },
                    onClearOutcome = { viewModel.clearArrivalOutcome() },
                    onBack = {
                        screenMode = VisitsScreenMode.List
                        viewModel.clearArrivalOutcome()
                    },
                    onConfirm = { draft, allowDuplicate ->
                        viewModel.recordArrival(draft, allowDuplicate)
                    },
                    dark = dark,
                )
            }
            is VisitsScreenMode.Departure -> {
                val activeUserName = user?.fullName?.takeIf { it.isNotBlank() } ?: user?.email
                val liveVisit = uiState.openVisits.firstOrNull { it.id == mode.visitId }
                    ?: uiState.recentCompleted.firstOrNull { it.id == mode.visitId }
                    ?: uiState.conflictVisits.firstOrNull { it.id == mode.visitId }
                    ?: mode.fallbackVisit
                val history by remember(liveVisit.id) {
                    viewModel.observeVisitHistory(liveVisit.id)
                }.collectAsStateWithLifecycle(initialValue = emptyList())

                LaunchedEffect(liveVisit.id) {
                    viewModel.refreshVisitHistory(liveVisit.id)
                }

                GateDepartureFullPage(
                    visit = liveVisit,
                    history = history,
                    elapsedTick = elapsedTick,
                    focusDeparture = mode.focusDeparture,
                    canDepart = canDepart,
                    canEditVisit = if (liveVisit.status == VisitStatus.Completed) canCorrectCritical else canEditOpen,
                    canResolveConflicts = canResolveConflicts,
                    currentUserName = activeUserName,
                    isSubmittingDeparture = uiState.isSubmittingDeparture,
                    departureError = (uiState.departureOutcome as? AuthResult.Failure)?.error?.message,
                    isSubmittingCorrection = uiState.isSubmittingCorrection,
                    correctionOutcome = uiState.correctionOutcome,
                    isSyncBusy = uiState.isSyncing,
                    onClearCorrectionOutcome = { viewModel.clearCorrectionOutcome() },
                    onBack = {
                        screenMode = VisitsScreenMode.List
                        viewModel.clearDepartureOutcome()
                        viewModel.clearCorrectionOutcome()
                    },
                    onConfirmDeparture = { notes, updatedSeals ->
                        viewModel.recordDeparture(liveVisit.id, notes, updatedSeals)
                    },
                    onSaveCorrection = { draft ->
                        viewModel.recordCorrection(
                            draft = draft,
                            canCorrectCritical = canCorrectCritical,
                            editorName = user?.fullName,
                        )
                    },
                    onResolveConflictKeepDevice = {
                        viewModel.resolveConflictKeepLocal(liveVisit.id) { }
                    },
                    onResolveConflictUseServer = {
                        viewModel.resolveConflictUseServer(liveVisit.id) { }
                    },
                    dark = dark,
                )
            }
        }
    }
}

// =============================================================================
// SCREEN 1: VISITS LIST OVERVIEW
// =============================================================================

@Composable
private fun VisitsListContent(
    uiState: VisitsUiState,
    elapsedTick: Int,
    canArrive: Boolean,
    canDepart: Boolean,
    dark: Boolean,
    onMenuClick: () -> Unit,
    onLogArrivalClick: () -> Unit,
    onVisitClick: (TruckVisit) -> Unit,
    onQuickDepartClick: (TruckVisit) -> Unit,
    onShowCompletedChanged: (Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onTypeFilterSelected: (VisitType?) -> Unit,
    onManualRetrySync: () -> Unit,
    onPullToRefresh: () -> Unit,
    onDismissBanner: () -> Unit,
    onReviewConflicts: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    var isCompactHeader by remember { mutableStateOf(false) }
    var pullOffsetPx by remember { mutableFloatStateOf(0f) }
    val refreshThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    val maxPullPx = remember(density) { with(density) { 120.dp.toPx() } }

    // Reset pull offset when sync finishes
    LaunchedEffect(uiState.isSyncing) {
        if (!uiState.isSyncing) {
            pullOffsetPx = 0f
        }
    }

    val nestedScrollConnection = remember(listState, uiState.isSyncing) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If user is pushing back up while pull-to-refresh is partially pulled down, consume it first
                if (available.y < 0f && pullOffsetPx > 0f) {
                    val consumedY = available.y.coerceAtLeast(-pullOffsetPx)
                    pullOffsetPx = (pullOffsetPx + consumedY).coerceAtLeast(0f)
                    return Offset(0f, consumedY)
                }
                // Scrolling down the list (finger moving up): collapse Metrics Bar & Filter Chips
                if (source == NestedScrollSource.UserInput && available.y < -10f && !isCompactHeader) {
                    isCompactHeader = true
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (source == NestedScrollSource.UserInput && isAtTop && available.y > 0f) {
                    // First expand Metrics Bar & Filter Chips if they were collapsed
                    if (isCompactHeader && available.y > 8f) {
                        isCompactHeader = false
                    }
                    // Accumulate pull-to-refresh offset with smooth resistance
                    if (!uiState.isSyncing) {
                        pullOffsetPx = (pullOffsetPx + available.y * 0.55f).coerceIn(0f, maxPullPx)
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullOffsetPx >= refreshThresholdPx && !uiState.isSyncing) {
                    pullOffsetPx = 0f
                    onPullToRefresh()
                } else {
                    pullOffsetPx = 0f
                }
                return Velocity.Zero
            }
        }
    }

    val pullProgress = (pullOffsetPx / refreshThresholdPx).coerceIn(0f, 1f)
    val showPullIndicator = pullOffsetPx > 8f || uiState.isSyncing

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (canArrive) {
                LiquidArrivalFab(
                    onClick = onLogArrivalClick,
                    dark = dark,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.End,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .nestedScroll(nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // Liquid Glass Header Strip
            LiquidVisitsHeader(
                lastSyncedAt = uiState.lastSyncedAt,
                isSyncing = uiState.isSyncing,
                onMenuClick = onMenuClick,
                dark = dark,
            )

            // Pull-Down to Refresh Indicator
            AnimatedVisibility(
                visible = showPullIndicator,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CapsuleShape)
                            .background(extras.glassFill)
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (pullProgress >= 1f || uiState.isSyncing) {
                                        scheme.primary.copy(alpha = 0.55f)
                                    } else {
                                        extras.glassBorder
                                    },
                                ),
                                CapsuleShape,
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = scheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = if (pullProgress >= 1f) scheme.primary else scheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Text(
                            text = when {
                                uiState.isSyncing -> "Refreshing visits from server..."
                                pullProgress >= 1f -> "Release to refresh"
                                else -> "Pull down to refresh"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (pullProgress >= 1f || uiState.isSyncing) {
                                    scheme.primary
                                } else {
                                    scheme.onSurfaceVariant
                                },
                            ),
                        )
                    }
                }
            }

            // Sync Failure / Conflict Banner (shown ONLY after repeated auto-sync failures, conflicts, or session expiry)
            uiState.syncAlertBanner?.let { banner ->
                LiquidSyncBanner(
                    banner = banner,
                    isSyncing = uiState.isSyncing,
                    onRetryClick = onManualRetrySync,
                    onReviewConflicts = onReviewConflicts,
                    onDismissMessage = onDismissBanner,
                    dark = dark,
                )
            }

            // Auto-Collapsing Yard Capacity & Status Metrics Strip (collapses when scrolling down)
            AnimatedVisibility(
                visible = !isCompactHeader,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                YardMetricsBar(
                    openVisits = uiState.openVisits,
                    dark = dark,
                )
            }

            // STICKY: On Yard / Departed Segmented Switch (always pinned at the top)
            YardTabSwitch(
                openCount = uiState.openVisits.size,
                completedCount = uiState.recentCompleted.size,
                showCompleted = uiState.showCompleted,
                onSelect = onShowCompletedChanged,
                dark = dark,
            )

            // STICKY: Search Bar (always pinned at the top)
            LiquidSearchBar(
                searchQuery = uiState.searchQuery,
                onQueryChange = onSearchQueryChanged,
                dark = dark,
            )

            // Auto-Collapsing Filter Chips Row (collapses when scrolling down)
            AnimatedVisibility(
                visible = !isCompactHeader,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                LiquidFilterChipsRow(
                    selectedFilter = uiState.selectedTypeFilter,
                    onSelectFilter = onTypeFilterSelected,
                    dark = dark,
                )
            }

            // Scrollable Visits List (always scrollable so pull-to-refresh & collapse work with any item count)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (uiState.filteredVisits.isEmpty()) {
                    item(key = "empty_visits_state") {
                        LiquidEmptyVisitsState(
                            isFiltered = uiState.searchQuery.isNotBlank() || uiState.selectedTypeFilter != null,
                            showCompleted = uiState.showCompleted,
                            canArrive = canArrive,
                            dark = dark,
                            onLogArrivalClick = onLogArrivalClick,
                        )
                    }
                } else {
                    items(uiState.filteredVisits, key = { it.id }) { visit ->
                        LiquidActiveVisitCard(
                            visit = visit,
                            elapsedTick = elapsedTick,
                            onCardClick = { onVisitClick(visit) },
                            dark = dark,
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// SCREEN 2: ARRIVAL FULL PAGE (NOT A MODAL)
// =============================================================================

@Composable
private fun GateArrivalFullPage(
    openVisits: List<TruckVisit>,
    plateSuggestions: List<String>,
    isSubmitting: Boolean,
    arrivalOutcome: ArrivalOutcome?,
    currentUserName: String? = null,
    onPlateQueryChange: (String) -> Unit,
    onClearOutcome: () -> Unit,
    onBack: () -> Unit,
    onConfirm: (draft: ArrivalDraft, allowDuplicate: Boolean) -> Unit,
    dark: Boolean,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors

    // 3 MANDATORY (NON-OPTIONAL) FIELDS:
    var countryCodeInput by remember { mutableStateOf("ZM") }
    var customCountryReasonInput by remember { mutableStateOf("") }
    var frontPlateInput by remember { mutableStateOf("") }
    var horsePlateInput by remember { mutableStateOf("") }
    var isFrontManuallySet by remember { mutableStateOf(false) }
    var isHorseManuallySet by remember { mutableStateOf(false) }

    // Bidirectional auto-refill helpers between Front Plate and Horse Plate:
    fun updateFrontPlate(raw: String) {
        val cleaned = raw.take(20).uppercase()
        frontPlateInput = cleaned
        isFrontManuallySet = cleaned.isNotBlank()
        if (!isHorseManuallySet) {
            horsePlateInput = cleaned
        }
    }

    fun updateHorsePlate(raw: String) {
        val cleaned = raw.take(20).uppercase()
        horsePlateInput = cleaned
        isHorseManuallySet = cleaned.isNotBlank()
        if (!isFrontManuallySet) {
            frontPlateInput = cleaned
        }
    }

    // TRANSPORTER / CARRIER FIELD WITH SEPARATE LIST & DROPDOWN:
    var transporterInput by remember { mutableStateOf("") }
    var isTransporterFocused by remember { mutableStateOf(false) }

    // LIVE OCR SCANNER MODAL & BACKGROUND PHOTO EXTRACTION STATE:
    var activeScannerTarget by remember { mutableStateOf<String?>(null) } // "FRONT", "HORSE", or "TRANSPORTER"
    var pendingCaptureTarget by remember { mutableStateOf<String?>(null) }
    var extractingTarget by remember { mutableStateOf<String?>(null) }

    val knownTransporterNames = remember {
        TransporterStore.getKnownTransporters(context).toSet()
    }

    val takePictureOcrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        val target = pendingCaptureTarget
        pendingCaptureTarget = null
        if (bitmap != null && target != null) {
            extractingTarget = target
            val mode = if (target == "TRANSPORTER") OcrScanMode.TRANSPORTER else OcrScanMode.PLATE
            PlateTextAnalyzer.extractFromBitmap(
                bitmap = bitmap,
                scanMode = mode,
                knownTransporters = knownTransporterNames,
            ) { res ->
                extractingTarget = null
                val extracted = res?.plateNumber
                val matchedCarrier = res?.matchedCarrier
                if (!extracted.isNullOrBlank()) {
                    when (target) {
                        "FRONT" -> updateFrontPlate(extracted)
                        "HORSE" -> updateHorsePlate(extracted)
                        "TRANSPORTER" -> transporterInput = (matchedCarrier ?: extracted).take(80)
                    }
                }
            }
        }
    }

    val captureCameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingCaptureTarget != null) {
            takePictureOcrLauncher.launch(null)
        } else {
            pendingCaptureTarget = null
        }
    }

    fun launchPhotoOcrCapture(target: String) {
        pendingCaptureTarget = target
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            takePictureOcrLauncher.launch(null)
        } else {
            captureCameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // OPTIONAL FIELDS:
    var selectedType by remember { mutableStateOf(VisitType.Quick) } // Quick is the first category
    var customPurposeInput by remember { mutableStateOf("") }
    var driverNameInput by remember { mutableStateOf("") }
    var driverPhoneDigits by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var cargoDescriptionInput by remember { mutableStateOf("") }
    var cargoItems by remember { mutableStateOf<List<CargoItem>>(emptyList()) }
    var seals by remember { mutableStateOf<List<VisitSeal>>(emptyList()) }
    var customFields by remember { mutableStateOf<List<CustomVisitField>>(emptyList()) }
    var pendingDuplicateDraft by remember { mutableStateOf<ArrivalDraft?>(null) }
    var pendingDuplicateVisit by remember { mutableStateOf<TruckVisit?>(null) }

    val cleanCountry = countryCodeInput.trim().uppercase().take(4)
    val cleanFrontPlate = normalizePlate(frontPlateInput)
    val cleanHorsePlate = normalizePlate(horsePlateInput)

    // Query live vehicle history suggestions as user types front or horse plate
    LaunchedEffect(cleanFrontPlate, cleanHorsePlate) {
        val activePrefix = when {
            cleanFrontPlate.length >= 2 -> cleanFrontPlate
            cleanHorsePlate.length >= 2 -> cleanHorsePlate
            else -> ""
        }
        onPlateQueryChange(activePrefix)
    }

    // Also handle server-side OPEN_VISIT_EXISTS if an open visit exists remotely
    LaunchedEffect(arrivalOutcome) {
        if (arrivalOutcome is ArrivalOutcome.OpenVisitExists) {
            pendingDuplicateVisit = arrivalOutcome.existing
        }
    }

    // Autocomplete dropdown matching results
    val matchingTransporters = remember(transporterInput, isTransporterFocused) {
        if (isTransporterFocused && transporterInput.isNotBlank()) {
            TransporterStore.searchTransporters(context, transporterInput)
        } else emptyList()
    }

    // Top known carriers for fast one-tap entry
    val knownCarriers = remember {
        TransporterStore.getKnownTransporters(context).take(5)
    }

    // Check duplicate open visits for either Front plate or Horse plate
    val existingOpenVisit = remember(cleanFrontPlate, cleanHorsePlate, openVisits) {
        if (cleanFrontPlate.isEmpty() && cleanHorsePlate.isEmpty()) null
        else openVisits.find { visit ->
            val info = extractPlateInfo(visit.plateNumber, visit.arrivalNotes)
            (cleanFrontPlate.isNotEmpty() && (visit.plateNumber == cleanFrontPlate || info.frontPlate == cleanFrontPlate)) ||
                (cleanHorsePlate.isNotEmpty() && (visit.plateNumber == cleanHorsePlate || info.horsePlate == cleanHorsePlate))
        }
    }

    // Phone is optional: if entered, accepts international length 7..15 digits
    val isPhoneValid = driverPhoneDigits.isEmpty() || driverPhoneDigits.length in 7..15

    // ONLY 3 THINGS ARE NOT OPTIONAL: country, front plate, and horse plate!
    val canSave = !isSubmitting &&
        cleanCountry.isNotBlank() &&
        cleanFrontPlate.isNotBlank() &&
        cleanHorsePlate.isNotBlank() &&
        isPhoneValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // Full Page Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(extras.glassFill)
                    .border(BorderStroke(1.dp, extras.glassBorder), CircleShape)
                    .clickable(
                        enabled = !isSubmitting,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column {
                Text(
                    text = stringResource(R.string.dialog_arrival_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onSurface,
                        letterSpacing = (-0.5).sp,
                    ),
                )
                Text(
                    text = "Register inbound vehicle onto yard",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Scrollable Body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // CARD 1: VEHICLE PLATES (3 REQUIRED FIELDS)
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "VEHICLE IDENTIFICATION (REQUIRED)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = scheme.primary,
                            letterSpacing = 0.8.sp,
                        ),
                    )

                    // Field 1: Country Code (SADC selection + manual entry + default Zambia ZM)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.lbl_country),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                ),
                            )
                            Text(
                                text = "Required",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = if (cleanCountry.isNotBlank()) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                        SadcCountrySelectorField(
                            countryCode = countryCodeInput,
                            onCountryCodeChange = { countryCodeInput = it },
                            customCountryReason = customCountryReasonInput,
                            onCustomCountryReasonChange = { customCountryReasonInput = it },
                            dark = dark,
                        )
                    }

                    // Field 2: Front Plate
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.lbl_front_plate),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                    ),
                                )
                                Text(
                                    text = "Required",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = if (cleanFrontPlate.isNotBlank()) Color(0xFF10B981) else Color(0xFFEF4444),
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }

                            // Camera Scan & Capture Trigger Buttons for Front Plate
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(CapsuleShape)
                                        .background(extras.glassFill)
                                        .border(BorderStroke(0.8.dp, extras.glassBorder), CapsuleShape)
                                        .clickable(enabled = extractingTarget != "FRONT") {
                                            launchPhotoOcrCapture("FRONT")
                                        }
                                        .padding(horizontal = 9.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Capture Front Plate Photo",
                                        tint = scheme.onSurface,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = if (extractingTarget == "FRONT") "Extracting..." else "Capture",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = scheme.onSurface,
                                        ),
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(CapsuleShape)
                                        .background(scheme.primary.copy(alpha = if (dark) 0.16f else 0.10f))
                                        .border(BorderStroke(0.8.dp, scheme.primary.copy(alpha = 0.35f)), CapsuleShape)
                                        .clickable { activeScannerTarget = "FRONT" }
                                        .padding(horizontal = 9.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Scan Front Plate",
                                        tint = scheme.primary,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = "Scan",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = scheme.primary,
                                        ),
                                    )
                                }
                            }
                        }
                        LiquidTextInput(
                            value = frontPlateInput,
                            onValueChange = { updateFrontPlate(it) },
                            placeholder = stringResource(R.string.hint_front_plate),
                            isMonospace = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            dark = dark,
                        )
                    }

                    // Field 3: Horse Plate
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.lbl_horse_plate),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                    ),
                                )
                                Text(
                                    text = "Required",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = if (cleanHorsePlate.isNotBlank()) Color(0xFF10B981) else Color(0xFFEF4444),
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }

                            // Camera Scan & Capture Trigger Buttons for Horse Plate
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(CapsuleShape)
                                        .background(extras.glassFill)
                                        .border(BorderStroke(0.8.dp, extras.glassBorder), CapsuleShape)
                                        .clickable(enabled = extractingTarget != "HORSE") {
                                            launchPhotoOcrCapture("HORSE")
                                        }
                                        .padding(horizontal = 9.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Capture Horse Plate Photo",
                                        tint = scheme.onSurface,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = if (extractingTarget == "HORSE") "Extracting..." else "Capture",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = scheme.onSurface,
                                        ),
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(CapsuleShape)
                                        .background(scheme.primary.copy(alpha = if (dark) 0.16f else 0.10f))
                                        .border(BorderStroke(0.8.dp, scheme.primary.copy(alpha = 0.35f)), CapsuleShape)
                                        .clickable { activeScannerTarget = "HORSE" }
                                        .padding(horizontal = 9.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Scan Horse Plate",
                                        tint = scheme.primary,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = "Scan",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = scheme.primary,
                                        ),
                                    )
                                }
                            }
                        }
                        LiquidTextInput(
                            value = horsePlateInput,
                            onValueChange = { updateHorsePlate(it) },
                            placeholder = stringResource(R.string.hint_horse_plate),
                            isMonospace = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            dark = dark,
                        )
                    }

                    // Real-Time Live Dual Plate Preview (Stacked vertically so Horse sits below Front)
                    if (cleanFrontPlate.isNotBlank() || cleanHorsePlate.isNotBlank()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                text = "Live Physical Plate Preview:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (cleanFrontPlate.isNotBlank()) {
                                    InternationalPlateBadge(
                                        countryCode = cleanCountry.ifBlank { "ZM" },
                                        plateNumber = cleanFrontPlate,
                                        plateLabel = "FRONT",
                                        dark = dark,
                                    )
                                }
                                if (cleanHorsePlate.isNotBlank()) {
                                    InternationalPlateBadge(
                                        countryCode = cleanCountry.ifBlank { "ZM" },
                                        plateNumber = cleanHorsePlate,
                                        plateLabel = "HORSE",
                                        dark = dark,
                                    )
                                }
                            }
                        }
                    }

                    // Recent Plate Suggestions
                    if (plateSuggestions.isNotEmpty() && (frontPlateInput.length >= 2 || horsePlateInput.length >= 2)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Recent Yard Suggestions (tap to auto-fill):",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(plateSuggestions) { plate ->
                                    Box(
                                        modifier = Modifier
                                            .clip(CapsuleShape)
                                            .background(scheme.primary.copy(alpha = if (dark) 0.20f else 0.12f))
                                            .border(
                                                BorderStroke(0.8.dp, scheme.primary.copy(alpha = 0.4f)),
                                                CapsuleShape,
                                            )
                                            .clickable {
                                                if (!isFrontManuallySet) {
                                                    updateFrontPlate(plate)
                                                } else {
                                                    updateHorsePlate(plate)
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    ) {
                                        Text(
                                            text = plate,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = scheme.primary,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    CategoryCustomFieldsEditor(
                        category = VisitFieldCategory.Vehicle,
                        customFields = customFields,
                        onCustomFieldsChange = { customFields = it },
                        dark = dark,
                    )
                }
            }

            // DUPLICATE VISIT NOTICE (Backend automatically tracks and links duplicates)
            if (existingOpenVisit != null) {
                val amberColor = Color(0xFFF59E0B)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(amberColor.copy(alpha = if (dark) 0.16f else 0.10f))
                        .border(BorderStroke(1.dp, amberColor.copy(alpha = 0.45f)), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = amberColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Truck already on yard (${existingOpenVisit.reference ?: "Active"})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = amberColor,
                                ),
                            )
                            Text(
                                text = "Automatic background sync will link this visit automatically.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }

            var isTransporterExpanded by remember { mutableStateOf(false) }
            var isPurposeExpanded by remember { mutableStateOf(false) }
            var isDriverExpanded by remember { mutableStateOf(false) }
            var isNotesExpanded by remember { mutableStateOf(false) }

            // CARD 2: TRANSPORTER / CARRIER DETAILS (WITH AUTOCOMPLETE DROPDOWN)
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { isTransporterExpanded = !isTransporterExpanded },
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "TRANSPORTER / CARRIER (OPTIONAL)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = scheme.primary,
                                letterSpacing = 0.8.sp,
                            ),
                        )
                        Icon(
                            imageVector = if (isTransporterExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isTransporterExpanded) "Collapse" else "Expand",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    AnimatedVisibility(
                        visible = isTransporterExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.lbl_transporter),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                    ),
                                )

                                // Camera Scan & Capture Trigger Buttons for Transporter Marking / Logo
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(CapsuleShape)
                                            .background(extras.glassFill)
                                            .border(BorderStroke(0.8.dp, extras.glassBorder), CapsuleShape)
                                            .clickable(enabled = extractingTarget != "TRANSPORTER") {
                                                launchPhotoOcrCapture("TRANSPORTER")
                                            }
                                            .padding(horizontal = 9.dp, vertical = 4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoCamera,
                                            contentDescription = "Capture Transporter Photo",
                                            tint = scheme.onSurface,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = if (extractingTarget == "TRANSPORTER") "Extracting..." else "Capture",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = scheme.onSurface,
                                            ),
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(CapsuleShape)
                                            .background(scheme.primary.copy(alpha = if (dark) 0.16f else 0.10f))
                                            .border(BorderStroke(0.8.dp, scheme.primary.copy(alpha = 0.35f)), CapsuleShape)
                                            .clickable { activeScannerTarget = "TRANSPORTER" }
                                            .padding(horizontal = 9.dp, vertical = 4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Scan Transporter Marking",
                                            tint = scheme.primary,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = "Scan",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = scheme.primary,
                                            ),
                                        )
                                    }
                                }
                            }

                            // Transporter text input with focus listener
                            LiquidTextInput(
                                value = transporterInput,
                                onValueChange = {
                                    transporterInput = it.take(80)
                                    isTransporterFocused = true
                                },
                                placeholder = stringResource(R.string.hint_transporter),
                                leadingIcon = Icons.Default.LocalShipping,
                                onFocusChanged = { isTransporterFocused = it },
                                dark = dark,
                            )

                            // Autocomplete Dropdown popup list
                            if (isTransporterFocused && transporterInput.isNotBlank() && matchingTransporters.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(extras.glassFill)
                                        .border(
                                            BorderStroke(1.dp, scheme.primary.copy(alpha = 0.45f)),
                                            RoundedCornerShape(12.dp),
                                        )
                                        .padding(6.dp),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Matching Transporters (tap to select):",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                color = scheme.onSurfaceVariant,
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                        matchingTransporters.forEach { carrier ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        transporterInput = carrier
                                                        isTransporterFocused = false
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LocalShipping,
                                                    contentDescription = null,
                                                    tint = scheme.primary,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                Text(
                                                    text = carrier,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = scheme.onSurface,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Known carriers fast-selection pills
                            if (knownCarriers.isNotEmpty() && transporterInput.isBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    Text(
                                        text = "Frequent Carriers (tap to auto-fill):",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            color = scheme.onSurfaceVariant,
                                        ),
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(knownCarriers) { carrier ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(CapsuleShape)
                                                    .background(scheme.primary.copy(alpha = if (dark) 0.18f else 0.10f))
                                                    .border(
                                                        BorderStroke(0.8.dp, scheme.primary.copy(alpha = 0.35f)),
                                                        CapsuleShape,
                                                    )
                                                    .clickable { transporterInput = carrier }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                            ) {
                                                Text(
                                                    text = carrier,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp,
                                                        color = scheme.primary,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            CategoryCustomFieldsEditor(
                                category = VisitFieldCategory.Transporter,
                                customFields = customFields,
                                onCustomFieldsChange = { customFields = it },
                                dark = dark,
                            )
                        }
                    }
                }
            }

            // CARD 3: VISIT PURPOSE (QUICK FIRST, WITH "OTHER" CUSTOM PURPOSE)
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { isPurposeExpanded = !isPurposeExpanded },
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "VISIT PURPOSE (OPTIONAL)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = scheme.primary,
                                letterSpacing = 0.8.sp,
                            ),
                        )
                        Icon(
                            imageVector = if (isPurposeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isPurposeExpanded) "Collapse" else "Expand",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    AnimatedVisibility(
                        visible = isPurposeExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Quick is the first category in the segmented row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                VisitType.entries.forEach { type ->
                                    val isSelected = selectedType == type
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(CapsuleShape)
                                            .background(
                                                if (isSelected) {
                                                    Brush.linearGradient(
                                                        listOf(
                                                            scheme.primary,
                                                            scheme.secondary,
                                                        ),
                                                    )
                                                } else {
                                                    Brush.linearGradient(
                                                        listOf(
                                                            scheme.surfaceVariant.copy(alpha = 0.45f),
                                                            scheme.surfaceVariant.copy(alpha = 0.45f),
                                                        ),
                                                    )
                                                },
                                            )
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    if (isSelected) Color.White.copy(alpha = 0.5f) else extras.glassBorder,
                                                ),
                                                CapsuleShape,
                                            )
                                            .clickable { selectedType = type }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = type.label.split(" ").first(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                fontSize = 11.sp,
                                            ),
                                            color = if (isSelected) Color.White else scheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            // On press "Other", allow the user to specify custom purpose
                            if (selectedType == VisitType.Other) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    Text(
                                        text = stringResource(R.string.lbl_custom_purpose),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = scheme.onSurface,
                                        ),
                                    )
                                    LiquidTextInput(
                                        value = customPurposeInput,
                                        onValueChange = { customPurposeInput = it.take(80) },
                                        placeholder = stringResource(R.string.hint_custom_purpose),
                                        dark = dark,
                                    )
                                }
                            }

                            CategoryCustomFieldsEditor(
                                category = VisitFieldCategory.Purpose,
                                customFields = customFields,
                                onCustomFieldsChange = { customFields = it },
                                dark = dark,
                            )
                        }
                    }
                }
            }

            // CARD 4: DRIVER & CONTACT INFO (OPTIONAL)
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { isDriverExpanded = !isDriverExpanded },
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "DRIVER & CONTACT INFO (OPTIONAL)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = scheme.primary,
                                letterSpacing = 0.8.sp,
                            ),
                        )
                        Icon(
                            imageVector = if (isDriverExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isDriverExpanded) "Collapse" else "Expand",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    AnimatedVisibility(
                        visible = isDriverExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.lbl_driver_name),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSurface,
                                    ),
                                )
                                LiquidTextInput(
                                    value = driverNameInput,
                                    onValueChange = { driverNameInput = it.take(100) },
                                    placeholder = stringResource(R.string.hint_driver_name),
                                    leadingIcon = Icons.Default.Person,
                                    dark = dark,
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.lbl_driver_phone),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSurface,
                                    ),
                                )
                                // Phone number with '+' already visible; user types directly
                                LiquidInternationalPhoneInput(
                                    phoneDigits = driverPhoneDigits,
                                    onPhoneDigitsChange = { driverPhoneDigits = it },
                                    isValid = driverPhoneDigits.length in 7..15,
                                    dark = dark,
                                )
                                Text(
                                    text = "Carrier contact number with international country code.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    ),
                                )
                            }

                            CategoryCustomFieldsEditor(
                                category = VisitFieldCategory.Driver,
                                customFields = customFields,
                                onCustomFieldsChange = { customFields = it },
                                dark = dark,
                            )
                        }
                    }
                }
            }

            // CARD 5: CARGO & SEALS REGISTER (OPTIONAL)
            CargoAndSealsEditorCard(
                cargoDescription = cargoDescriptionInput,
                onCargoDescriptionChange = { cargoDescriptionInput = it },
                cargoItems = cargoItems,
                onCargoItemsChange = { cargoItems = it },
                seals = seals,
                onSealsChange = { seals = it },
                customFields = customFields,
                onCustomFieldsChange = { customFields = it },
                dark = dark,
            )

            // CARD 6: INSPECTION AND GENERAL NOTES IF AVAILABLE (OPTIONAL)
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { isNotesExpanded = !isNotesExpanded },
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.lbl_arrival_notes),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = scheme.primary,
                                letterSpacing = 0.8.sp,
                            ),
                        )
                        Icon(
                            imageVector = if (isNotesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isNotesExpanded) "Collapse" else "Expand",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    AnimatedVisibility(
                        visible = isNotesExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LiquidTextInput(
                                value = notesInput,
                                onValueChange = { notesInput = it.take(300) },
                                placeholder = stringResource(R.string.hint_arrival_notes),
                                maxLines = 3,
                                dark = dark,
                            )
                            CategoryCustomFieldsEditor(
                                category = VisitFieldCategory.Notes,
                                customFields = customFields,
                                onCustomFieldsChange = { customFields = it },
                                dark = dark,
                            )
                        }
                    }
                }
            }

            if (arrivalOutcome is ArrivalOutcome.Invalid) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.error.copy(alpha = 0.14f))
                        .border(BorderStroke(1.dp, scheme.error.copy(alpha = 0.45f)), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        text = arrivalOutcome.message,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = scheme.error,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }

            // BOTTOM ACTION BUTTONS: Confirm Arrival on top, Cancel at the bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Confirm Arrival button on top
                LiquidActionButton(
                    text = if (isSubmitting) "Saving Arrival..." else stringResource(R.string.btn_confirm_arrival),
                    enabled = canSave,
                    dark = dark,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // Persist transporter to local separate store for future autocomplete
                        if (transporterInput.isNotBlank()) {
                            TransporterStore.addTransporter(context, transporterInput)
                        }

                        val fullPhone = if (driverPhoneDigits.isNotEmpty()) "+$driverPhoneDigits" else null
                        val countryReasonTag = if (customCountryReasonInput.isNotBlank()) {
                            "[CountryReason: ${sanitizeTagPart(customCountryReasonInput)}] "
                        } else ""
                        val purposeTag = if (selectedType == VisitType.Other && customPurposeInput.isNotBlank()) {
                            "[Purpose: ${sanitizeTagPart(customPurposeInput)}] "
                        } else ""
                        val transporterTag = if (transporterInput.isNotBlank()) {
                            "[Transporter: ${sanitizeTagPart(transporterInput)}] "
                        } else ""
                        val customFieldsTags = encodeCustomFieldsTags(customFields).let {
                            if (it.isNotBlank()) "$it " else ""
                        }
                        val arrivedByTag = if (!currentUserName.isNullOrBlank()) {
                            "[ArrivedBy: ${sanitizeTagPart(currentUserName)}] "
                        } else ""
                        val combinedNotes = "[Country: $cleanCountry] [Front: $cleanFrontPlate] [Horse: $cleanHorsePlate] $arrivedByTag$countryReasonTag$transporterTag$purposeTag$customFieldsTags${notesInput.trim()}".trim()

                        val draft = ArrivalDraft(
                            plateNumber = cleanFrontPlate.take(20),
                            visitType = selectedType,
                            driverName = driverNameInput.ifBlank { null },
                            driverPhone = fullPhone,
                            notes = combinedNotes.ifBlank { null },
                            cargoDescription = cargoDescriptionInput.trim().ifBlank { null },
                            cargoItems = cargoItems,
                            seals = seals,
                        )
                        if (existingOpenVisit != null) {
                            pendingDuplicateDraft = draft
                            pendingDuplicateVisit = existingOpenVisit
                        } else {
                            pendingDuplicateDraft = draft
                            onConfirm(draft, false)
                        }
                    },
                )

                // Cancel button at the bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CapsuleShape)
                        .background(extras.glassFill)
                        .border(BorderStroke(1.dp, extras.glassBorder), CapsuleShape)
                        .clickable(enabled = !isSubmitting, onClick = onBack)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // Duplicate open visit confirmation dialog
    val dupVisit = pendingDuplicateVisit
    val dupDraft = pendingDuplicateDraft
    if (dupVisit != null && dupDraft != null) {
        Dialog(onDismissRequest = {
            pendingDuplicateVisit = null
            pendingDuplicateDraft = null
            onClearOutcome()
        }) {
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 20) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = stringResource(R.string.warn_duplicate_open_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = scheme.onSurface,
                            ),
                        )
                    }
                    Text(
                        text = dupVisit.syncError
                            ?: "Truck ${dupDraft.plateNumber} already has an active open visit (${dupVisit.reference ?: "Active"}). Do you want to allow a second open visit for this truck?",
                        style = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurfaceVariant),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidActionButton(
                            text = "Allow & Log Arrival",
                            enabled = !isSubmitting,
                            dark = dark,
                            onClick = {
                                pendingDuplicateVisit = null
                                pendingDuplicateDraft = null
                                onClearOutcome()
                                onConfirm(dupDraft, true)
                            },
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CapsuleShape)
                                .background(extras.glassFill)
                                .border(BorderStroke(1.dp, extras.glassBorder), CapsuleShape)
                                .clickable {
                                    pendingDuplicateVisit = null
                                    pendingDuplicateDraft = null
                                    onClearOutcome()
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.btn_cancel),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    // CameraX Live OCR Scanner Modal
    activeScannerTarget?.let { target ->
        val label = when (target) {
            "FRONT" -> "Front Plate"
            "HORSE" -> "Horse Plate"
            "TRANSPORTER" -> "Transporter / Carrier"
            else -> "Plate"
        }

        val isTransporter = target == "TRANSPORTER"

        PlateScannerDialog(
            targetLabel = label,
            scanMode = if (isTransporter) OcrScanMode.TRANSPORTER else OcrScanMode.PLATE,
            dark = dark,
            onPlateScanned = { scannedValue, matchedCarrier ->
                when (target) {
                    "FRONT" -> {
                        updateFrontPlate(scannedValue)
                    }
                    "HORSE" -> {
                        updateHorsePlate(scannedValue)
                    }
                    "TRANSPORTER" -> {
                        transporterInput = matchedCarrier ?: scannedValue
                    }
                }
                activeScannerTarget = null
            },
            onDismiss = { activeScannerTarget = null },
        )
    }
}

// =============================================================================
// SCREEN 3: DEPARTURE FULL PAGE (NOT A MODAL)
// =============================================================================

@Composable
private fun GateDepartureFullPage(
    visit: TruckVisit,
    history: List<VisitCorrectionRecord>,
    elapsedTick: Int,
    focusDeparture: Boolean = false,
    canDepart: Boolean,
    canEditVisit: Boolean,
    canResolveConflicts: Boolean,
    currentUserName: String? = null,
    isSubmittingDeparture: Boolean,
    departureError: String?,
    isSubmittingCorrection: Boolean,
    correctionOutcome: AuthResult<TruckVisit>?,
    isSyncBusy: Boolean,
    onClearCorrectionOutcome: () -> Unit,
    onBack: () -> Unit,
    onConfirmDeparture: (notes: String?, updatedSeals: List<VisitSeal>?) -> Unit,
    onSaveCorrection: (VisitCorrectionDraft) -> Unit,
    onResolveConflictKeepDevice: () -> Unit,
    onResolveConflictUseServer: () -> Unit,
    dark: Boolean,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    var departureNotes by remember(visit.id) { mutableStateOf("") }
    var exitFrontPlateInput by remember(visit.id) { mutableStateOf("") }
    var exitHorsePlateInput by remember(visit.id) { mutableStateOf("") }
    var isExitFrontManuallySet by remember(visit.id) { mutableStateOf(false) }
    var isExitHorseManuallySet by remember(visit.id) { mutableStateOf(false) }
    var departureSeals by remember(visit.id, visit.seals) { mutableStateOf(visit.seals) }
    var departureCustomFields by remember(visit.id) { mutableStateOf<List<CustomVisitField>>(emptyList()) }
    var exitScanTarget by remember { mutableStateOf<String?>(null) }
    var pendingExitCaptureTarget by remember { mutableStateOf<String?>(null) }
    var extractingExitTarget by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }
    var isExitPlateExpanded by remember(visit.id) { mutableStateOf(true) }
    var isDepartureNotesExpanded by remember(visit.id) { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val isCompleted = visit.status == VisitStatus.Completed
    val plateInfo = remember(visit.plateNumber, visit.arrivalNotes, visit.departureNotes) {
        extractPlateInfo(visit.plateNumber, visit.arrivalNotes, visit.departureNotes)
    }

    // Bidirectional auto-refill helpers between Exit Front Plate and Exit Horse Plate:
    fun updateExitFrontPlate(raw: String) {
        val cleaned = raw.uppercase().take(20)
        exitFrontPlateInput = cleaned
        isExitFrontManuallySet = cleaned.isNotBlank()
        if (!isExitHorseManuallySet) {
            exitHorsePlateInput = when {
                cleaned.isBlank() -> ""
                normalizePlate(cleaned) == plateInfo.frontPlate -> plateInfo.horsePlate
                else -> cleaned
            }
        }
    }

    fun updateExitHorsePlate(raw: String) {
        val cleaned = raw.uppercase().take(20)
        exitHorsePlateInput = cleaned
        isExitHorseManuallySet = cleaned.isNotBlank()
        if (!isExitFrontManuallySet) {
            exitFrontPlateInput = when {
                cleaned.isBlank() -> ""
                normalizePlate(cleaned) == plateInfo.horsePlate -> plateInfo.frontPlate
                else -> cleaned
            }
        }
    }

    val exitTakePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        val target = pendingExitCaptureTarget
        pendingExitCaptureTarget = null
        if (bitmap != null && target != null) {
            extractingExitTarget = target
            PlateTextAnalyzer.extractFromBitmap(
                bitmap = bitmap,
                scanMode = OcrScanMode.PLATE,
                knownTransporters = emptySet(),
            ) { res ->
                extractingExitTarget = null
                val extracted = res?.plateNumber
                if (!extracted.isNullOrBlank()) {
                    if (target == "HORSE") {
                        updateExitHorsePlate(extracted)
                    } else {
                        updateExitFrontPlate(extracted)
                    }
                }
            }
        }
    }

    val exitCaptureCameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingExitCaptureTarget != null) {
            exitTakePictureLauncher.launch(null)
        } else {
            pendingExitCaptureTarget = null
        }
    }

    fun launchExitPhotoOcrCapture(target: String) {
        pendingExitCaptureTarget = target
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            exitTakePictureLauncher.launch(null)
        } else {
            exitCaptureCameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val displayArrivalNotes = remember(visit.arrivalNotes) {
        cleanDisplayNotes(visit.arrivalNotes)
    }
    val displayDepartureNotes = remember(visit.departureNotes) {
        cleanDisplayNotes(visit.departureNotes)
    }
    val stayDurationLabel = remember(visit.arrivedAt, visit.departedAt, elapsedTick) {
        formatElapsedDuration(
            arrivedAt = visit.arrivedAt,
            endAt = visit.departedAt ?: Instant.now(),
            isCompleted = visit.status == VisitStatus.Completed,
        )
    }

    LaunchedEffect(focusDeparture) {
        if (focusDeparture && !isCompleted) {
            delay(150)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    LaunchedEffect(correctionOutcome) {
        if (correctionOutcome is AuthResult.Success) {
            showCorrectionDialog = false
            onClearCorrectionOutcome()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // Full Page Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(extras.glassFill)
                    .border(BorderStroke(1.dp, extras.glassBorder), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCompleted) "Visit Details" else stringResource(R.string.dialog_departure_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onSurface,
                        letterSpacing = (-0.5).sp,
                    ),
                )
                Text(
                    text = if (isCompleted) {
                        "Completed stay record & audit trail"
                    } else {
                        "Confirm vehicle clearance & departure"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            }

            if (canEditVisit) {
                Box(
                    modifier = Modifier
                        .clip(CapsuleShape)
                        .background(scheme.primary.copy(alpha = if (dark) 0.18f else 0.10f))
                        .border(BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f)), CapsuleShape)
                        .clickable {
                            onClearCorrectionOutcome()
                            showCorrectionDialog = true
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Visit",
                            tint = scheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = if (isCompleted) "Correct" else "Edit",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                            ),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Scrollable Body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (visit.syncState == SyncState.Conflict) {
                ConflictResolutionCard(
                    visit = visit,
                    canResolve = canResolveConflicts,
                    isBusy = isSyncBusy,
                    dark = dark,
                    onKeepDevice = onResolveConflictKeepDevice,
                    onUseServer = onResolveConflictUseServer,
                )
            }

            // CARD 1: VEHICLE SUMMARY & TIMESTAMPS
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            InternationalPlateBadge(
                                countryCode = plateInfo.country,
                                plateNumber = plateInfo.frontPlate,
                                plateLabel = "FRONT",
                                dark = dark,
                            )
                            if (plateInfo.horsePlate.isNotBlank()) {
                                InternationalPlateBadge(
                                    countryCode = plateInfo.country,
                                    plateNumber = plateInfo.horsePlate,
                                    plateLabel = "HORSE",
                                    dark = dark,
                                )
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (visit.isEdited) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CapsuleShape)
                                            .background(Color(0xFF6366F1).copy(alpha = if (dark) 0.22f else 0.12f))
                                            .border(
                                                BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.45f)),
                                                CapsuleShape,
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        Text(
                                            text = "Edited",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF6366F1),
                                            ),
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(CapsuleShape)
                                        .background(scheme.primary.copy(alpha = if (dark) 0.20f else 0.12f))
                                        .border(BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f)), CapsuleShape)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = plateInfo.customPurpose ?: visit.visitType.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = scheme.primary,
                                        ),
                                    )
                                }
                            }
                            if (visit.syncState != SyncState.Synced) {
                                LiquidSyncBadge(state = visit.syncState)
                            }
                        }
                    }

                    val countryFlag = countryFlagForCode(plateInfo.country)
                    val countryLabel = countryNameForCode(plateInfo.country)
                        ?: plateInfo.customCountryReason?.takeIf { it.isNotBlank() }
                        ?: plateInfo.country
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$countryFlag $countryLabel (${plateInfo.country})",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurfaceVariant,
                            ),
                        )
                        visit.reference?.let { ref ->
                            Text(
                                text = "#$ref",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = scheme.primary,
                                ),
                            )
                        }
                    }

                    CategoryCustomFieldsReadOnlyList(
                        category = VisitFieldCategory.Vehicle,
                        customFields = plateInfo.customFields,
                        dark = dark,
                    )

                    plateInfo.transporter?.let { carrier ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Transporter: $carrier",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                ),
                            )
                        }
                    }

                    CategoryCustomFieldsReadOnlyList(
                        category = VisitFieldCategory.Transporter,
                        customFields = plateInfo.customFields,
                        dark = dark,
                    )

                    CategoryCustomFieldsReadOnlyList(
                        category = VisitFieldCategory.Purpose,
                        customFields = plateInfo.customFields,
                        dark = dark,
                    )

                    // Exact arrival / departure timestamps & staff audit info
                    val checkedInByLabel = plateInfo.arrivedByName
                        ?: currentUserName?.takeIf { it.isNotBlank() }
                        ?: "Gate Staff"
                    val checkedOutByLabel = plateInfo.departedByName
                        ?: currentUserName?.takeIf { it.isNotBlank() }
                        ?: "Gate Staff"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(extras.glassFill)
                            .border(BorderStroke(1.dp, extras.glassBorder), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Arrived",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                            Text(
                                text = formatVisitTimestamp(visit.arrivedAt),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                ),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Checked In By",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = checkedInByLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                    ),
                                )
                            }
                        }
                        visit.departedAt?.let { depAt ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Departed",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSurfaceVariant,
                                    ),
                                )
                                Text(
                                    text = formatVisitTimestamp(depAt),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                    ),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Exited / Checked Out By",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSurfaceVariant,
                                    ),
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = scheme.primary,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = checkedOutByLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = scheme.onSurface,
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    // Elapsed stay highlight - Centered
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.primary.copy(alpha = if (dark) 0.15f else 0.08f))
                            .border(BorderStroke(1.dp, scheme.primary.copy(alpha = 0.25f)), RoundedCornerShape(14.dp))
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = if (isCompleted) "Total Stay Duration" else "Total Time On Yard",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSurfaceVariant,
                                    ),
                                )
                            }
                            Text(
                                text = stayDurationLabel,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = scheme.onSurface,
                                    letterSpacing = (-0.5).sp,
                                ),
                            )
                        }
                    }

                    if (!visit.driverName.isNullOrBlank() || !visit.driverPhone.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            visit.driverName?.let { name ->
                                Text(
                                    text = "Driver: $name",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSurface,
                                    ),
                                )
                            }
                            visit.driverPhone?.let { phone ->
                                Text(
                                    text = "Phone: $phone",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = scheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }

                    CategoryCustomFieldsReadOnlyList(
                        category = VisitFieldCategory.Driver,
                        customFields = plateInfo.customFields,
                        dark = dark,
                    )

                    if (!displayArrivalNotes.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(extras.glassFill)
                                .border(BorderStroke(1.dp, extras.glassBorder), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = "ARRIVAL NOTES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.6.sp,
                                    color = scheme.primary,
                                ),
                            )
                            Text(
                                text = displayArrivalNotes,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = scheme.onSurface,
                                ),
                            )
                        }
                    }

                    CategoryCustomFieldsReadOnlyList(
                        category = VisitFieldCategory.Notes,
                        customFields = plateInfo.customFields,
                        dark = dark,
                    )

                    if (isCompleted) {
                        plateInfo.exitVerifiedPlate?.takeIf { it.isNotBlank() }?.let { exitPlate ->
                            val matchesArrival = exitPlate == plateInfo.frontPlate || exitPlate == plateInfo.horsePlate
                            val badgeColor = if (matchesArrival) Color(0xFF10B981) else Color(0xFFF59E0B)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(badgeColor.copy(alpha = if (dark) 0.16f else 0.10f))
                                    .border(BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (matchesArrival) Icons.Default.Check else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(16.dp),
                                )
                                Column {
                                    Text(
                                        text = if (matchesArrival) {
                                            "EXIT PLATE VERIFIED: $exitPlate"
                                        } else {
                                            "EXIT PLATE MISMATCH: $exitPlate (Expected ${plateInfo.frontPlate})"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = badgeColor,
                                        ),
                                    )
                                }
                            }
                        }

                        if (!displayDepartureNotes.isNullOrBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(extras.glassFill)
                                    .border(BorderStroke(1.dp, extras.glassBorder), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = "DEPARTURE NOTES",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.6.sp,
                                        color = Color(0xFFEF4444),
                                    ),
                                )
                                Text(
                                    text = displayDepartureNotes,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = scheme.onSurface,
                                    ),
                                )
                            }
                        }

                        CategoryCustomFieldsReadOnlyList(
                            category = VisitFieldCategory.Departure,
                            customFields = plateInfo.customFields,
                            dark = dark,
                        )
                    }
                }
            }

            // CARD 2: CARGO & SEALS REGISTER
            CargoAndSealsReadOnlySection(
                visit = visit,
                customFields = plateInfo.customFields,
                dark = dark,
            )

            // CARD 3: AUDIT & CORRECTION HISTORY
            if (history.isNotEmpty()) {
                VisitCorrectionHistoryCard(history = history, dark = dark)
            }

            // DEPARTURE INPUT & ACTIONS (ONLY WHEN OPEN)
            if (!isCompleted) {
                if (canDepart) {
                    // 1. EXIT PLATE OCR VERIFICATION CARD (OPTIONAL)
                    val normalizedExitFront = normalizePlate(exitFrontPlateInput)
                    val normalizedExitHorse = normalizePlate(exitHorsePlateInput)
                    val isFrontMatched = normalizedExitFront.isNotEmpty() && normalizedExitFront == plateInfo.frontPlate
                    val isFrontMismatched = normalizedExitFront.isNotEmpty() && !isFrontMatched
                    val isHorseMatched = normalizedExitHorse.isNotEmpty() && normalizedExitHorse == plateInfo.horsePlate
                    val isHorseMismatched = normalizedExitHorse.isNotEmpty() && !isHorseMatched
                    val verifiedCount = (if (isFrontMatched) 1 else 0) + (if (isHorseMatched) 1 else 0)

                    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { isExitPlateExpanded = !isExitPlateExpanded },
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "EXIT PLATE VERIFICATION (OPTIONAL)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = scheme.primary,
                                            letterSpacing = 0.8.sp,
                                        ),
                                    )
                                    if (verifiedCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CapsuleShape)
                                                .background(Color(0xFF10B981).copy(alpha = if (dark) 0.22f else 0.14f))
                                                .border(BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.5f)), CapsuleShape)
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = "$verifiedCount/2 Verified",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFF10B981),
                                                ),
                                            )
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = if (isExitPlateExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExitPlateExpanded) "Collapse" else "Expand",
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            AnimatedVisibility(
                                visible = isExitPlateExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // SLOT 1: FRONT PLATE VERIFICATION
                                    val frontBorderColor = when {
                                        isFrontMatched -> Color(0xFF10B981).copy(alpha = 0.55f)
                                        isFrontMismatched -> Color(0xFFF59E0B).copy(alpha = 0.55f)
                                        else -> extras.glassBorder
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.26f else 0.42f))
                                            .border(BorderStroke(1.dp, frontBorderColor), RoundedCornerShape(14.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Text(
                                                        text = "Front Plate",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = scheme.onSurface,
                                                        ),
                                                    )
                                                    if (isFrontMatched) {
                                                        Text(
                                                            text = "✓ Verified",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = Color(0xFF10B981),
                                                            ),
                                                        )
                                                    } else if (isFrontMismatched) {
                                                        Text(
                                                            text = "⚠ Mismatch",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = Color(0xFFF59E0B),
                                                            ),
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Expected: ${plateInfo.frontPlate}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        color = scheme.onSurfaceVariant,
                                                    ),
                                                )
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                // Quick 1-tap Match pill
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CapsuleShape)
                                                        .background(
                                                            if (isFrontMatched) Color(0xFF10B981).copy(alpha = if (dark) 0.25f else 0.16f)
                                                            else extras.glassFill,
                                                        )
                                                        .border(
                                                            BorderStroke(
                                                                0.8.dp,
                                                                if (isFrontMatched) Color(0xFF10B981) else extras.glassBorder,
                                                            ),
                                                            CapsuleShape,
                                                        )
                                                        .clickable {
                                                            updateExitFrontPlate(if (isFrontMatched) "" else plateInfo.frontPlate)
                                                        }
                                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = if (isFrontMatched) Color(0xFF10B981) else scheme.onSurfaceVariant,
                                                            modifier = Modifier.size(12.dp),
                                                        )
                                                        Text(
                                                            text = "Match",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isFrontMatched) Color(0xFF10B981) else scheme.onSurface,
                                                            ),
                                                        )
                                                    }
                                                }

                                                // Direct Photo Capture + Background OCR Button for Front Plate
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier
                                                        .clip(CapsuleShape)
                                                        .background(extras.glassFill)
                                                        .border(BorderStroke(0.8.dp, extras.glassBorder), CapsuleShape)
                                                        .clickable(enabled = extractingExitTarget != "FRONT") {
                                                            launchExitPhotoOcrCapture("FRONT")
                                                        }
                                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PhotoCamera,
                                                        contentDescription = "Capture Front Plate Photo",
                                                        tint = scheme.onSurface,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                    Text(
                                                        text = if (extractingExitTarget == "FRONT") "Extracting..." else "Capture",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp,
                                                            color = scheme.onSurface,
                                                        ),
                                                    )
                                                }

                                                // Dedicated Scan Front Camera Button
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier
                                                        .clip(CapsuleShape)
                                                        .background(scheme.primary.copy(alpha = if (dark) 0.20f else 0.12f))
                                                        .border(BorderStroke(0.8.dp, scheme.primary.copy(alpha = 0.4f)), CapsuleShape)
                                                        .clickable { exitScanTarget = "FRONT" }
                                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CameraAlt,
                                                        contentDescription = "Scan Front Plate",
                                                        tint = scheme.primary,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                    Text(
                                                        text = "Scan",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp,
                                                            color = scheme.primary,
                                                        ),
                                                    )
                                                }
                                            }
                                        }

                                        LiquidTextInput(
                                            value = exitFrontPlateInput,
                                            onValueChange = { updateExitFrontPlate(it) },
                                            placeholder = "Front plate number",
                                            isMonospace = true,
                                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                            dark = dark,
                                        )
                                    }

                                    // SLOT 2: HORSE PLATE VERIFICATION
                                    val horseBorderColor = when {
                                        isHorseMatched -> Color(0xFF10B981).copy(alpha = 0.55f)
                                        isHorseMismatched -> Color(0xFFF59E0B).copy(alpha = 0.55f)
                                        else -> extras.glassBorder
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.26f else 0.42f))
                                            .border(BorderStroke(1.dp, horseBorderColor), RoundedCornerShape(14.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Text(
                                                        text = "Horse Plate",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = scheme.onSurface,
                                                        ),
                                                    )
                                                    if (isHorseMatched) {
                                                        Text(
                                                            text = "✓ Verified",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = Color(0xFF10B981),
                                                            ),
                                                        )
                                                    } else if (isHorseMismatched) {
                                                        Text(
                                                            text = "⚠ Mismatch",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = Color(0xFFF59E0B),
                                                            ),
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Expected: ${plateInfo.horsePlate}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        color = scheme.onSurfaceVariant,
                                                    ),
                                                )
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                // Quick 1-tap Match pill
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CapsuleShape)
                                                        .background(
                                                            if (isHorseMatched) Color(0xFF10B981).copy(alpha = if (dark) 0.25f else 0.16f)
                                                            else extras.glassFill,
                                                        )
                                                        .border(
                                                            BorderStroke(
                                                                0.8.dp,
                                                                if (isHorseMatched) Color(0xFF10B981) else extras.glassBorder,
                                                            ),
                                                            CapsuleShape,
                                                        )
                                                        .clickable {
                                                            updateExitHorsePlate(if (isHorseMatched) "" else plateInfo.horsePlate)
                                                        }
                                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = if (isHorseMatched) Color(0xFF10B981) else scheme.onSurfaceVariant,
                                                            modifier = Modifier.size(12.dp),
                                                        )
                                                        Text(
                                                            text = "Match",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isHorseMatched) Color(0xFF10B981) else scheme.onSurface,
                                                            ),
                                                        )
                                                    }
                                                }

                                                // Direct Photo Capture + Background OCR Button for Horse Plate
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier
                                                        .clip(CapsuleShape)
                                                        .background(extras.glassFill)
                                                        .border(BorderStroke(0.8.dp, extras.glassBorder), CapsuleShape)
                                                        .clickable(enabled = extractingExitTarget != "HORSE") {
                                                            launchExitPhotoOcrCapture("HORSE")
                                                        }
                                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PhotoCamera,
                                                        contentDescription = "Capture Horse Plate Photo",
                                                        tint = scheme.onSurface,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                    Text(
                                                        text = if (extractingExitTarget == "HORSE") "Extracting..." else "Capture",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp,
                                                            color = scheme.onSurface,
                                                        ),
                                                    )
                                                }

                                                // Dedicated Scan Horse Camera Button
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier
                                                        .clip(CapsuleShape)
                                                        .background(scheme.primary.copy(alpha = if (dark) 0.20f else 0.12f))
                                                        .border(BorderStroke(0.8.dp, scheme.primary.copy(alpha = 0.4f)), CapsuleShape)
                                                        .clickable { exitScanTarget = "HORSE" }
                                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CameraAlt,
                                                        contentDescription = "Scan Horse Plate",
                                                        tint = scheme.primary,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                    Text(
                                                        text = "Scan",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp,
                                                            color = scheme.primary,
                                                        ),
                                                    )
                                                }
                                            }
                                        }

                                        LiquidTextInput(
                                            value = exitHorsePlateInput,
                                            onValueChange = { updateExitHorsePlate(it) },
                                            placeholder = "Horse plate number",
                                            isMonospace = true,
                                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                            dark = dark,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. EXIT SEAL & CARGO VERIFICATION CARD (OPTIONAL)
                    DepartureSealVerificationCard(
                        seals = departureSeals,
                        onSealsChange = { departureSeals = it },
                        dark = dark,
                    )

                    // 3. DEPARTURE NOTES & CUSTOM COLUMNS CARD (OPTIONAL)
                    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { isDepartureNotesExpanded = !isDepartureNotesExpanded },
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "INSPECTION & DEPARTURE NOTES (OPTIONAL)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = scheme.primary,
                                        letterSpacing = 0.8.sp,
                                    ),
                                )
                                Icon(
                                    imageVector = if (isDepartureNotesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isDepartureNotesExpanded) "Collapse" else "Expand",
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            AnimatedVisibility(
                                visible = isDepartureNotesExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Quick-tap gate clearance remarks
                                    val quickRemarks = remember {
                                        listOf("Cleared at Gate", "Seals Intact", "Loaded Out", "Empty Trailer")
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        quickRemarks.forEach { remark ->
                                            val isSelected = departureNotes.contains(remark, ignoreCase = true)
                                            Box(
                                                modifier = Modifier
                                                    .clip(CapsuleShape)
                                                    .background(
                                                        if (isSelected) scheme.primary.copy(alpha = if (dark) 0.24f else 0.14f)
                                                        else extras.glassFill,
                                                    )
                                                    .border(
                                                        BorderStroke(
                                                            0.8.dp,
                                                            if (isSelected) scheme.primary else extras.glassBorder,
                                                        ),
                                                        CapsuleShape,
                                                    )
                                                    .clickable {
                                                        departureNotes = if (departureNotes.isBlank()) {
                                                            remark
                                                        } else if (!isSelected) {
                                                            "$departureNotes, $remark".take(300)
                                                        } else {
                                                            departureNotes
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                            ) {
                                                Text(
                                                    text = remark,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                                                    ),
                                                )
                                            }
                                        }
                                    }

                                    LiquidTextInput(
                                        value = departureNotes,
                                        onValueChange = { departureNotes = it.take(300) },
                                        placeholder = stringResource(R.string.hint_departure_notes),
                                        maxLines = 3,
                                        dark = dark,
                                    )
                                    CategoryCustomFieldsEditor(
                                        category = VisitFieldCategory.Departure,
                                        customFields = departureCustomFields,
                                        onCustomFieldsChange = { departureCustomFields = it },
                                        dark = dark,
                                    )
                                }
                            }
                        }
                    }

                    if (!departureError.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.error.copy(alpha = if (dark) 0.18f else 0.10f))
                                .border(BorderStroke(1.dp, scheme.error.copy(alpha = 0.45f)), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = departureError,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.error,
                                ),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CapsuleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFFEF4444).copy(alpha = if (isSubmittingDeparture) 0.65f else 1f),
                                            Color(0xFFDC2626).copy(alpha = if (isSubmittingDeparture) 0.65f else 1f),
                                        ),
                                    ),
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.5f),
                                                Color.Transparent,
                                            ),
                                        ),
                                    ),
                                    CapsuleShape,
                                )
                                .clickable(enabled = !isSubmittingDeparture) { showConfirmDialog = true }
                                .drawBehind {
                                    drawRoundRect(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.5f),
                                                Color.Transparent,
                                            ),
                                        ),
                                        size = Size(size.width, 2.dp.toPx()),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(999.dp.toPx()),
                                    )
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSubmittingDeparture) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                    )
                                    Text(
                                        text = "Logging Departure...",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                        ),
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.btn_confirm_departure),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            letterSpacing = 0.3.sp,
                                        ),
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CapsuleShape)
                                .background(extras.glassFill)
                                .border(BorderStroke(1.dp, extras.glassBorder), CapsuleShape)
                                .clickable(onClick = onBack)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.btn_cancel),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                } else {
                    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "View-only access. Departure sign-off requires gate runner permissions.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // Confirmation Modal Dialog
    if (showConfirmDialog && !isCompleted && canDepart) {
        Dialog(
            onDismissRequest = { showConfirmDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showConfirmDialog = false },
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (dark) Color(0xFF131B2A).copy(alpha = 0.96f)
                            else Color(0xFFFFFFFF).copy(alpha = 0.98f)
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                if (dark) Color.White.copy(alpha = 0.15f)
                                else Color.Black.copy(alpha = 0.08f),
                            ),
                            RoundedCornerShape(24.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* prevent closing when clicking inside dialog card */ },
                        )
                        .padding(24.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Departure Icon with red halo
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                                .border(BorderStroke(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.35f)), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(26.dp),
                            )
                        }

                        // Title & Prompt
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Confirm Departure",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = scheme.onSurface,
                                    letterSpacing = (-0.5).sp,
                                ),
                            )
                            Text(
                                text = "Are you sure you want to log departure and clear this vehicle from the yard?",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = scheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                ),
                            )
                        }

                        // Plate & stay summary card inside dialog
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.45f))
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f),
                                    ),
                                    RoundedCornerShape(14.dp),
                                )
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            InternationalPlateBadge(
                                countryCode = plateInfo.country,
                                plateNumber = plateInfo.frontPlate,
                                plateLabel = "FRONT",
                                dark = dark,
                            )
                            if (plateInfo.horsePlate != plateInfo.frontPlate) {
                                InternationalPlateBadge(
                                    countryCode = plateInfo.country,
                                    plateNumber = plateInfo.horsePlate,
                                    plateLabel = "HORSE",
                                    dark = dark,
                                )
                            }
                            visit.reference?.let { ref ->
                                Text(
                                    text = "Token: #$ref  •  Stay: $stayDurationLabel",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = scheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }

                        // Action Buttons: Confirm on top, Cancel at the bottom
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CapsuleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFFEF4444),
                                                Color(0xFFDC2626),
                                            ),
                                        ),
                                    )
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.White.copy(alpha = 0.5f),
                                                    Color.Transparent,
                                                ),
                                            ),
                                        ),
                                        CapsuleShape,
                                    )
                                    .clickable(enabled = !isSubmittingDeparture) {
                                        showConfirmDialog = false
                                        val cleanExitFront = normalizePlate(exitFrontPlateInput).takeIf { it.isNotBlank() }
                                        val cleanExitHorse = normalizePlate(exitHorsePlateInput).takeIf { it.isNotBlank() }
                                        val cleanExitPlate = listOfNotNull(cleanExitFront, cleanExitHorse)
                                            .distinct()
                                            .joinToString(" / ")
                                            .takeIf { it.isNotBlank() }
                                        val customDepTags = encodeCustomFieldsTags(departureCustomFields)
                                        val cleanDepText = departureNotes.trim()
                                        val combinedDepartureNotes = buildString {
                                            if (!currentUserName.isNullOrBlank()) {
                                                append("[DepartedBy: ${sanitizeTagPart(currentUserName)}] ")
                                            }
                                            if (cleanExitPlate != null) {
                                                append("[ExitPlate: ${sanitizeTagPart(cleanExitPlate)}] ")
                                            }
                                            if (customDepTags.isNotBlank()) {
                                                append("$customDepTags ")
                                            }
                                            if (cleanDepText.isNotBlank()) {
                                                append(cleanDepText)
                                            }
                                        }.trim().takeIf { it.isNotBlank() }
                                        onConfirmDeparture(combinedDepartureNotes, departureSeals)
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Confirm",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                        ),
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CapsuleShape)
                                    .background(extras.glassFill)
                                    .border(BorderStroke(1.dp, extras.glassBorder), CapsuleShape)
                                    .clickable { showConfirmDialog = false }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_cancel),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    exitScanTarget?.let { target ->
        val expectedLabel = if (target == "HORSE") {
            "Exit Horse Plate (${plateInfo.horsePlate})"
        } else {
            "Exit Front Plate (${plateInfo.frontPlate})"
        }
        PlateScannerDialog(
            targetLabel = expectedLabel,
            scanMode = OcrScanMode.PLATE,
            dark = dark,
            onDismiss = { exitScanTarget = null },
            onPlateScanned = { scanned, _ ->
                if (target == "HORSE") {
                    updateExitHorsePlate(scanned)
                } else {
                    updateExitFrontPlate(scanned)
                }
                exitScanTarget = null
            },
        )
    }

    if (showCorrectionDialog && canEditVisit) {
        VisitCorrectionDialog(
            visit = visit,
            isSubmitting = isSubmittingCorrection,
            errorMessage = (correctionOutcome as? AuthResult.Failure)?.error?.message,
            dark = dark,
            onDismiss = {
                showCorrectionDialog = false
                onClearCorrectionOutcome()
            },
            onSave = onSaveCorrection,
        )
    }
}

// =============================================================================
// REUSABLE LIQUID GLASS COMPONENTS
// =============================================================================

@Composable
private fun LiquidVisitsHeader(
    lastSyncedAt: Instant?,
    isSyncing: Boolean,
    onMenuClick: () -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    val syncStatusText = when {
        isSyncing -> "Syncing..."
        lastSyncedAt != null -> "Synced ${formatVisitTimestamp(lastSyncedAt)}"
        else -> null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.visits_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = scheme.onSurface,
                    letterSpacing = (-0.5).sp,
                ),
            )
            Text(
                text = stringResource(R.string.visits_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
            )
            if (syncStatusText != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                            color = scheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        text = syncStatusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = scheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(extras.glassFill)
                .border(BorderStroke(1.dp, extras.glassBorder), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMenuClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = stringResource(R.string.nav_more),
                tint = scheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun YardTabSwitch(
    openCount: Int,
    completedCount: Int,
    showCompleted: Boolean,
    onSelect: (Boolean) -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    val selectedBrush = if (dark) {
        Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF38BDF8)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF0284C7)))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CapsuleShape)
            .background(extras.glassFill)
            .border(BorderStroke(1.dp, extras.glassBorder), CapsuleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            Triple("On Yard ($openCount)", false, !showCompleted),
            Triple("Departed ($completedCount)", true, showCompleted),
        ).forEach { (label, completedFlag, selected) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CapsuleShape)
                    .then(
                        if (selected) Modifier.background(selectedBrush)
                        else Modifier.background(Color.Transparent),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(completedFlag) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = if (selected) Color.White else scheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
private fun YardMetricsBar(
    openVisits: List<TruckVisit>,
    dark: Boolean,
) {
    val extras = TruckParkExtras.colors
    val scheme = MaterialTheme.colorScheme

    val quickCount = openVisits.count { it.visitType == VisitType.Quick }
    val overnightCount = openVisits.count { it.visitType == VisitType.Overnight }
    val transitCount = openVisits.count { it.visitType == VisitType.Transit || it.visitType == VisitType.Loading }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(extras.glassFill)
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = if (dark) 0.18f else 0.45f),
                        0.5f to extras.glassBorder,
                        1f to scheme.primary.copy(alpha = 0.2f),
                    ),
                ),
                RoundedCornerShape(18.dp),
            )
            .drawBehind {
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.12f else 0.25f),
                            Color.Transparent,
                        ),
                    ),
                    size = Size(size.width, 2.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                )
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetricStatPill(
                label = "Active Yard",
                value = openVisits.size.toString(),
                accent = scheme.primary,
            )
            MetricStatDivider(dark = dark)
            MetricStatPill(
                label = "Quick Stops",
                value = quickCount.toString(),
                accent = Color(0xFF10B981),
            )
            MetricStatDivider(dark = dark)
            MetricStatPill(
                label = "Overnight",
                value = overnightCount.toString(),
                accent = Color(0xFF6366F1),
            )
            MetricStatDivider(dark = dark)
            MetricStatPill(
                label = "Cargo/Transit",
                value = transitCount.toString(),
                accent = Color(0xFFF59E0B),
            )
        }
    }
}

@Composable
private fun MetricStatPill(
    label: String,
    value: String,
    accent: Color,
) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = accent,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                color = scheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun MetricStatDivider(dark: Boolean) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        if (dark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

@Composable
private fun LiquidSyncBanner(
    banner: SyncAlertBanner,
    isSyncing: Boolean,
    onRetryClick: () -> Unit,
    onReviewConflicts: () -> Unit,
    onDismissMessage: () -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = when (banner.severity) {
        SyncAlertSeverity.Info -> scheme.primary
        SyncAlertSeverity.Warning -> Color(0xFFF59E0B)
        SyncAlertSeverity.Error -> scheme.error
    }
    val title = when {
        banner.needsConflictReview -> "Conflict Review Required"
        banner.requiresSignIn -> "Session Expired"
        banner.severity == SyncAlertSeverity.Error -> "Sync Error"
        banner.severity == SyncAlertSeverity.Warning -> "Sync Warning"
        else -> "Sync Information"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = if (dark) 0.16f else 0.10f))
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.65f),
                            accent.copy(alpha = 0.25f),
                        ),
                    ),
                ),
                RoundedCornerShape(16.dp),
            )
            .drawBehind {
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    ),
                    size = Size(size.width, 2.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = scheme.onSurface,
                            ),
                        )
                        Text(
                            text = banner.message,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant,
                            ),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onDismissMessage)
                        .padding(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            if (banner.showRetry || banner.needsConflictReview) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (banner.showRetry) {
                        Box(
                            modifier = Modifier
                                .clip(CapsuleShape)
                                .background(accent)
                                .clickable(
                                    enabled = !isSyncing,
                                    onClick = onRetryClick,
                                )
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            if (isSyncing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                    )
                                    Text(
                                        text = "Retrying...",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                        ),
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "Retry Sync",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    if (banner.needsConflictReview) {
                        Box(
                            modifier = Modifier
                                .clip(CapsuleShape)
                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.55f else 0.75f))
                                .border(BorderStroke(1.dp, accent.copy(alpha = 0.5f)), CapsuleShape)
                                .clickable(onClick = onReviewConflicts)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = "Review Conflicts",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = accent,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidSearchBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    var isFocused by remember { mutableStateOf(false) }

    val searchBorderBrush = if (isFocused) {
        Brush.linearGradient(
            listOf(
                if (dark) Color(0xFF38BDF8) else Color(0xFF4F46E5),
                if (dark) Color(0xFF818CF8) else Color(0xFF0284C7),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (dark) 0.20f else 0.60f),
                extras.glassBorder,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CapsuleShape)
            .background(extras.glassFill)
            .border(BorderStroke(1.dp, searchBorderBrush), CapsuleShape)
            .drawBehind {
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.12f else 0.25f),
                            Color.Transparent,
                        ),
                    ),
                    size = Size(size.width, 2.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(999.dp.toPx()),
                )
            }
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (isFocused) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            BasicTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = scheme.onSurface,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(scheme.primary),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused },
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.hint_search_visits),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.65f),
                        )
                    }
                    inner()
                },
            )
            if (searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = scheme.onSurface,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidFilterChipsRow(
    selectedFilter: VisitType?,
    onSelectFilter: (VisitType?) -> Unit,
    dark: Boolean,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        item {
            LiquidFilterChip(
                label = stringResource(R.string.filter_all),
                isSelected = selectedFilter == null,
                onClick = { onSelectFilter(null) },
                dark = dark,
            )
        }
        items(VisitType.entries.toTypedArray()) { type ->
            LiquidFilterChip(
                label = type.label,
                isSelected = selectedFilter == type,
                onClick = { onSelectFilter(type) },
                dark = dark,
            )
        }
    }
}

@Composable
private fun LiquidFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors

    val activeBrush = if (dark) {
        Brush.linearGradient(
            listOf(
                Color(0xFF6366F1),
                Color(0xFF38BDF8),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFF4F46E5),
                Color(0xFF0284C7),
            ),
        )
    }

    Box(
        modifier = Modifier
            .clip(CapsuleShape)
            .background(if (isSelected) activeBrush else Brush.linearGradient(listOf(extras.glassFill, extras.glassFill)))
            .border(
                BorderStroke(
                    1.dp,
                    if (isSelected) {
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.6f),
                                scheme.primary.copy(alpha = 0.3f),
                            ),
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = if (dark) 0.15f else 0.40f),
                                extras.glassBorder,
                            ),
                        )
                    },
                ),
                CapsuleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .drawBehind {
                if (isSelected) {
                    drawRoundRect(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.Transparent,
                            ),
                        ),
                        size = Size(size.width, 2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(999.dp.toPx()),
                    )
                }
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.2.sp,
                fontSize = 11.sp,
            ),
            color = if (isSelected) Color.White else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiquidActiveVisitCard(
    visit: TruckVisit,
    elapsedTick: Int,
    onCardClick: () -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme

    val plateInfo = remember(visit.plateNumber, visit.arrivalNotes, visit.departureNotes) {
        extractPlateInfo(visit.plateNumber, visit.arrivalNotes, visit.departureNotes)
    }

    val typeColor = when (visit.visitType) {
        VisitType.Quick -> Color(0xFF10B981)
        VisitType.Overnight -> Color(0xFF6366F1)
        VisitType.Transit -> Color(0xFF8B5CF6)
        VisitType.Loading -> Color(0xFFF59E0B)
        VisitType.Other -> scheme.onSurfaceVariant
    }

    val displayNotes = remember(visit.arrivalNotes) {
        cleanDisplayNotes(visit.arrivalNotes)
    }
    val isCompleted = visit.status == VisitStatus.Completed
    val durationLabel = remember(visit.arrivedAt, visit.departedAt, isCompleted, elapsedTick) {
        formatElapsedDuration(
            arrivedAt = visit.arrivedAt,
            endAt = visit.departedAt ?: Instant.now(),
            isCompleted = isCompleted,
        )
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCardClick,
            ),
        contentPadding = 14,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Row 1: Stacked International Front & Horse Plate Badges + Visit Type / Sync / Edited Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // Dual Plates: Front on top, Horse down below
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InternationalPlateBadge(
                        countryCode = plateInfo.country,
                        plateNumber = plateInfo.frontPlate,
                        plateLabel = "FRONT",
                        dark = dark,
                    )

                    if (plateInfo.horsePlate != plateInfo.frontPlate) {
                        InternationalPlateBadge(
                            countryCode = plateInfo.country,
                            plateNumber = plateInfo.horsePlate,
                            plateLabel = "HORSE",
                            dark = dark,
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (visit.isEdited) {
                            Box(
                                modifier = Modifier
                                    .clip(CapsuleShape)
                                    .background(Color(0xFF6366F1).copy(alpha = if (dark) 0.22f else 0.12f))
                                    .border(
                                        BorderStroke(0.8.dp, Color(0xFF6366F1).copy(alpha = 0.45f)),
                                        CapsuleShape,
                                    )
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "Edited",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = Color(0xFF6366F1),
                                    ),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(CapsuleShape)
                                .background(typeColor.copy(alpha = if (dark) 0.18f else 0.12f))
                                .border(
                                    BorderStroke(1.dp, typeColor.copy(alpha = 0.45f)),
                                    CapsuleShape,
                                )
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(typeColor),
                                )
                                Text(
                                    text = formatVisitTimestamp(visit.arrivedAt),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = typeColor,
                                    ),
                                )
                            }
                        }
                    }

                    if (visit.syncState != SyncState.Synced) {
                        LiquidSyncBadge(state = visit.syncState)
                    }
                }
            }

            // Row 1.5: Transporter & Custom Country Detail Badges if present
            if (plateInfo.transporter != null || !plateInfo.customCountryReason.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    plateInfo.transporter?.let { carrier ->
                        Box(
                            modifier = Modifier
                                .clip(CapsuleShape)
                                .background(scheme.primary.copy(alpha = if (dark) 0.16f else 0.08f))
                                .border(BorderStroke(0.8.dp, scheme.primary.copy(alpha = 0.35f)), CapsuleShape)
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = carrier,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = scheme.primary,
                                    ),
                                )
                            }
                        }
                    }

                    plateInfo.customCountryReason?.takeIf { it.isNotBlank() }?.let { reason ->
                        Box(
                            modifier = Modifier
                                .clip(CapsuleShape)
                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.40f else 0.55f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = "${countryFlagForCode(plateInfo.country)} $reason",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }

            // Row 2: Driver Details
            if (!visit.driverName.isNullOrBlank() || !visit.driverPhone.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    visit.driverName?.let { name ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = scheme.onSurface,
                                ),
                            )
                        }
                    }

                    visit.driverPhone?.let { phone ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = phone,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }

            // Row 2.5: Cargo & Seal Summary Chips if recorded
            if (!visit.cargoDescription.isNullOrBlank() || visit.cargoItems.isNotEmpty() || visit.seals.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!visit.cargoDescription.isNullOrBlank() || visit.cargoItems.isNotEmpty()) {
                        val cargoSummary = when {
                            !visit.cargoDescription.isNullOrBlank() -> visit.cargoDescription
                            else -> "${visit.cargoItems.size} cargo item(s)"
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(CapsuleShape)
                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.40f else 0.55f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = cargoSummary,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = scheme.onSurface,
                                    ),
                                )
                            }
                        }
                    }
                    if (visit.seals.isNotEmpty()) {
                        val flaggedCount = visit.seals.count { it.condition != SealCondition.Intact }
                        val sealColor = if (flaggedCount > 0) scheme.error else Color(0xFF10B981)
                        Box(
                            modifier = Modifier
                                .clip(CapsuleShape)
                                .background(sealColor.copy(alpha = if (dark) 0.20f else 0.12f))
                                .border(BorderStroke(0.8.dp, sealColor.copy(alpha = 0.40f)), CapsuleShape)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = if (flaggedCount > 0) {
                                    "${visit.seals.size} seal(s) • $flaggedCount flagged"
                                } else {
                                    "${visit.seals.size} seal(s) intact"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = sealColor,
                                ),
                            )
                        }
                    }
                }
            }

            // Row 2.7: Custom Columns Summary Chips if recorded
            if (plateInfo.customFields.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val visibleFields = plateInfo.customFields.take(3)
                    visibleFields.forEach { field ->
                        Box(
                            modifier = Modifier
                                .clip(CapsuleShape)
                                .background(scheme.primary.copy(alpha = if (dark) 0.14f else 0.08f))
                                .border(BorderStroke(0.8.dp, scheme.primary.copy(alpha = 0.30f)), CapsuleShape)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = "${field.columnName}: ${field.value}",
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurface,
                                ),
                            )
                        }
                    }
                    val extraCount = plateInfo.customFields.size - visibleFields.size
                    if (extraCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CapsuleShape)
                                .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.40f else 0.55f))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = "+$extraCount more",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.primary,
                                ),
                            )
                        }
                    }
                }
            }

            // Row 3: Inspection and Cargo Notes
            displayNotes?.let { notes ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.45f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "“$notes”",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            // Subtle divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            // Row 4: Elapsed / Stay Duration + TP Reference in Middle + Details CTA
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (isCompleted && visit.departedAt != null) {
                            "Stay $durationLabel"
                        } else {
                            durationLabel
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = scheme.onSurface,
                        ),
                    )
                }

                visit.reference?.let { ref ->
                    Text(
                        text = "#$ref",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = scheme.onSurface,
                        ),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(CapsuleShape)
                        .background(scheme.primary.copy(alpha = if (dark) 0.14f else 0.08f))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Details",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = scheme.primary,
                        ),
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

/**
 * International metallic vehicle registration plate badge:
 * Emulates physical embossed aluminum plate with dynamic national country identifier.
 */
@Composable
private fun InternationalPlateBadge(
    countryCode: String,
    plateNumber: String,
    plateLabel: String? = null,
    dark: Boolean,
) {
    val plateBorder = if (dark) {
        Brush.linearGradient(
            listOf(
                Color(0xFF94A3B8),
                Color(0xFF475569),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFCBD5E1),
                Color(0xFF64748B),
            ),
        )
    }

    val plateBackground = if (dark) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF1E293B),
                Color(0xFF0F172A),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFFFFFFFF),
                Color(0xFFF1F5F9),
            ),
        )
    }

    Box(
        modifier = Modifier
            .clip(PlateShape)
            .background(plateBackground)
            .border(BorderStroke(1.2.dp, plateBorder), PlateShape)
            .drawBehind {
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.35f else 0.70f),
                            Color.Transparent,
                        ),
                    ),
                    size = Size(size.width, 1.5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                )
            }
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Dynamic Country strip (e.g. 🇿🇲 ZM, 🇿🇦 ZA, 🇹🇿 TZ, 🇨🇩 CD, 🇿🇼 ZW)
            val resolvedCode = countryCode.ifBlank { "ZM" }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF059669))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "${countryFlagForCode(resolvedCode)} $resolvedCode",
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp,
                        color = Color.White,
                        letterSpacing = 0.5.sp,
                    ),
                )
            }

            // Plate registration number
            Text(
                text = plateNumber,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp,
                    fontSize = 12.sp,
                    color = if (dark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                ),
            )

            // Optional plate label (e.g. "FRONT" or "HORSE")
            plateLabel?.let { label ->
                Text(
                    text = label,
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    ),
                )
            }
        }
    }
}

@Composable
private fun LiquidDepartureButton(
    onClick: () -> Unit,
    dark: Boolean,
) {
    val crimsonBase = Brush.linearGradient(
        listOf(
            Color(0xFFEF4444).copy(alpha = if (dark) 0.22f else 0.16f),
            Color(0xFFBE123C).copy(alpha = if (dark) 0.32f else 0.24f),
        ),
    )

    val crimsonRim = Brush.linearGradient(
        listOf(
            Color(0xFFF87171).copy(alpha = 0.85f),
            Color(0xFF991B1B).copy(alpha = 0.40f),
        ),
    )

    Box(
        modifier = Modifier
            .clip(CapsuleShape)
            .background(crimsonBase)
            .border(BorderStroke(1.dp, crimsonRim), CapsuleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .drawBehind {
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color.Transparent,
                        ),
                    ),
                    size = Size(size.width, 1.5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(999.dp.toPx()),
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                tint = if (dark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = "Depart",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (dark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                    letterSpacing = 0.2.sp,
                ),
            )
        }
    }
}

@Composable
private fun LiquidSyncBadge(state: SyncState) {
    val scheme = MaterialTheme.colorScheme
    val (label, color, icon) = when (state) {
        SyncState.Synced -> Triple("Synced", Color(0xFF10B981), Icons.Default.CloudDone)
        SyncState.Pending -> Triple("Pending", Color(0xFFF59E0B), Icons.Default.CloudUpload)
        SyncState.Conflict -> Triple("Conflict", scheme.error, Icons.Default.Warning)
        SyncState.Failed -> Triple("Failed", scheme.error, Icons.Default.Close)
    }

    Box(
        modifier = Modifier
            .clip(CapsuleShape)
            .background(color.copy(alpha = 0.12f))
            .border(BorderStroke(0.8.dp, color.copy(alpha = 0.35f)), CapsuleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                ),
            )
        }
    }
}

@Composable
private fun LiquidArrivalFab(
    onClick: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val fabBrush = if (dark) {
        Brush.linearGradient(
            listOf(
                Color(0xFF6366F1),
                Color(0xFF38BDF8),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFF4F46E5),
                Color(0xFF0284C7),
            ),
        )
    }

    val glowColor = if (dark) Color(0xFF38BDF8).copy(alpha = 0.45f) else Color(0xFF4F46E5).copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .drawBehind {
                drawRoundRect(
                    color = glowColor,
                    topLeft = Offset(0f, 6.dp.toPx()),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(999.dp.toPx()),
                )
            }
            .clip(CapsuleShape)
            .background(fabBrush)
            .border(
                BorderStroke(
                    1.2.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.85f),
                            Color.White.copy(alpha = 0.20f),
                        ),
                    ),
                ),
                CapsuleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .drawBehind {
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.65f),
                            Color.Transparent,
                        ),
                    ),
                    size = Size(size.width, 2.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(999.dp.toPx()),
                )
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.btn_log_arrival),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.btn_log_arrival),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 0.4.sp,
                ),
            )
        }
    }
}

// -----------------------------------------------------------------------------
// TEXT & INTERNATIONAL PHONE INPUTS
// -----------------------------------------------------------------------------

@Composable
internal fun LiquidTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isMonospace: Boolean = false,
    leadingIcon: ImageVector? = null,
    maxLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    var isFocused by remember { mutableStateOf(false) }

    val borderBrush = if (isFocused) {
        Brush.linearGradient(
            listOf(
                if (dark) Color(0xFF38BDF8) else Color(0xFF4F46E5),
                if (dark) Color(0xFF818CF8) else Color(0xFF0284C7),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (dark) 0.15f else 0.45f),
                extras.glassBorder,
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(extras.glassFill)
            .border(BorderStroke(1.dp, borderBrush), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leadingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFocused) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                    fontWeight = if (isMonospace) FontWeight.Bold else FontWeight.Normal,
                    letterSpacing = if (isMonospace) 1.2.sp else 0.sp,
                    color = scheme.onSurface,
                ),
                cursorBrush = SolidColor(scheme.primary),
                singleLine = maxLines == 1,
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        onFocusChanged?.invoke(focusState.isFocused)
                    },
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    inner()
                },
            )
        }
    }
}

/**
 * International Phone Number Input:
 * Pre-pends an international '+' capsule badge automatically.
 * User starts typing numbers directly without needing to type '+'.
 */
@Composable
internal fun LiquidInternationalPhoneInput(
    phoneDigits: String,
    onPhoneDigitsChange: (String) -> Unit,
    isValid: Boolean,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    var isFocused by remember { mutableStateOf(false) }

    val borderBrush = if (isFocused) {
        Brush.linearGradient(
            listOf(
                if (dark) Color(0xFF38BDF8) else Color(0xFF4F46E5),
                if (dark) Color(0xFF818CF8) else Color(0xFF0284C7),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (dark) 0.15f else 0.45f),
                extras.glassBorder,
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(extras.glassFill)
            .border(BorderStroke(1.dp, borderBrush), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // International '+' Prefix Pill (already there, non-editable)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                scheme.primary.copy(alpha = if (dark) 0.30f else 0.16f),
                                scheme.secondary.copy(alpha = if (dark) 0.20f else 0.10f),
                            ),
                        ),
                    )
                    .border(
                        BorderStroke(0.8.dp, scheme.primary.copy(alpha = 0.4f)),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = scheme.primary,
                            fontSize = 15.sp,
                        ),
                    )
                }
            }

            BasicTextField(
                value = phoneDigits,
                onValueChange = { input ->
                    // Strip any non-digit character and leading '+' if user pasted
                    val digits = input.filter { it.isDigit() }.take(15)
                    onPhoneDigitsChange(digits)
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    letterSpacing = 1.sp,
                ),
                cursorBrush = SolidColor(scheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused },
                decorationBox = { inner ->
                    if (phoneDigits.isEmpty()) {
                        Text(
                            text = stringResource(R.string.hint_driver_phone),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    inner()
                },
            )

            if (isValid && phoneDigits.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Valid international number",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun LiquidActionButton(
    text: String,
    enabled: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeBrush = if (dark) {
        Brush.linearGradient(
            listOf(
                Color(0xFF6366F1),
                Color(0xFF38BDF8),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFF4F46E5),
                Color(0xFF0284C7),
            ),
        )
    }

    val disabledBrush = Brush.linearGradient(
        listOf(
            Color.Gray.copy(alpha = 0.25f),
            Color.Gray.copy(alpha = 0.25f),
        ),
    )

    val borderStroke = if (enabled) {
        BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.7f),
                    Color.Transparent,
                ),
            ),
        )
    } else {
        BorderStroke(1.dp, Color.Transparent)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) activeBrush else disabledBrush)
            .border(borderStroke, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .drawBehind {
                if (enabled) {
                    drawRoundRect(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.5f),
                                Color.Transparent,
                            ),
                        ),
                        size = Size(size.width, 2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp,
            ),
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
        )
    }
}

// -----------------------------------------------------------------------------
// EMPTY STATE
// -----------------------------------------------------------------------------

@Composable
private fun LiquidEmptyVisitsState(
    isFiltered: Boolean,
    showCompleted: Boolean,
    canArrive: Boolean,
    dark: Boolean,
    onLogArrivalClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val titleText = when {
        isFiltered -> "No Matching Trucks"
        showCompleted -> "No Departed Trucks Yet"
        else -> stringResource(R.string.empty_visits_title)
    }
    val subtitleText = when {
        isFiltered -> "Try adjusting your search query or switching the visit type filter."
        showCompleted -> "Recently departed vehicles will appear here after clearance."
        else -> stringResource(R.string.empty_visits_subtitle)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 380.dp),
            contentPadding = 24,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    scheme.primary.copy(alpha = if (dark) 0.35f else 0.20f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.4f),
                                        scheme.primary.copy(alpha = 0.2f),
                                    ),
                                ),
                            ),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onSurface,
                    ),
                )

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                if (!isFiltered && !showCompleted && canArrive) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(CapsuleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF6366F1),
                                        Color(0xFF38BDF8),
                                    ),
                                ),
                            )
                            .clickable(onClick = onLogArrivalClick)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Log First Arrival",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// HELPERS
// -----------------------------------------------------------------------------

internal fun formatElapsedDuration(
    arrivedAt: Instant,
    endAt: Instant = Instant.now(),
    isCompleted: Boolean = false,
): String {
    val raw = Duration.between(arrivedAt, endAt)
    val duration = if (raw.isNegative) Duration.ZERO else raw
    val totalSeconds = duration.seconds
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    val seconds = totalSeconds % 60
    return when {
        hours <= 0 && minutes <= 0 -> {
            when {
                isCompleted && seconds > 0L -> "${seconds}s"
                isCompleted -> "< 1 min"
                seconds < 10L -> "Just now"
                else -> "${seconds}s"
            }
        }
        hours <= 0 && minutes == 1L -> "1 min"
        hours <= 0 -> "$minutes mins"
        hours < 24 && minutes == 0L -> "${hours}h"
        hours < 24 -> "${hours}h ${minutes}m"
        else -> {
            val days = hours / 24
            val remHours = hours % 24
            if (remHours > 0) "${days}d ${remHours}h" else "${days}d"
        }
    }
}
