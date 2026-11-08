package com.truckpark.management.feature.visits

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.truckpark.management.R
import com.truckpark.management.core.designsystem.components.GlassCard
import com.truckpark.management.core.designsystem.theme.TruckParkExtras
import com.truckpark.management.data.local.TransporterStore
import com.truckpark.management.domain.models.ArrivalDraft
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.domain.models.PlateInfo
import com.truckpark.management.domain.models.SyncState
import com.truckpark.management.domain.models.TruckVisit
import com.truckpark.management.domain.models.VisitType
import com.truckpark.management.domain.models.cleanDisplayNotes
import com.truckpark.management.domain.models.extractPlateInfo
import com.truckpark.management.domain.models.normalizePlate
import com.truckpark.management.feature.ocr.OcrScanMode
import com.truckpark.management.feature.ocr.PlateScannerDialog
import java.time.Duration
import java.time.Instant

private val CapsuleShape = RoundedCornerShape(999.dp)
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
    data class Departure(val visit: TruckVisit) : VisitsScreenMode
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

    var screenMode by remember { mutableStateOf<VisitsScreenMode>(VisitsScreenMode.List) }

    // Toggle bottom navigation bar visibility based on active sub-flow
    LaunchedEffect(screenMode) {
        onToggleBottomNav(screenMode == VisitsScreenMode.List)
    }

    // Intercept back navigation when in full-page sub-flows
    BackHandler(enabled = screenMode != VisitsScreenMode.List) {
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
                    dark = dark,
                    onMenuClick = onMenuClick,
                    onLogArrivalClick = { screenMode = VisitsScreenMode.Arrival },
                    onVisitClick = { visit -> screenMode = VisitsScreenMode.Departure(visit) },
                    onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onTypeFilterSelected = { viewModel.onTypeFilterSelected(it) },
                    onTriggerSync = { viewModel.triggerSync() },
                    onDismissBanner = { viewModel.dismissBanner() },
                )
            }
            is VisitsScreenMode.Arrival -> {
                GateArrivalFullPage(
                    openVisits = uiState.openVisits,
                    plateSuggestions = uiState.plateSuggestions,
                    onBack = {
                        screenMode = VisitsScreenMode.List
                        viewModel.clearArrivalOutcome()
                    },
                    onConfirm = { draft, allowDuplicate ->
                        viewModel.recordArrival(draft, allowDuplicate)
                        screenMode = VisitsScreenMode.List
                    },
                    dark = dark,
                )
            }
            is VisitsScreenMode.Departure -> {
                GateDepartureFullPage(
                    visit = mode.visit,
                    onBack = {
                        screenMode = VisitsScreenMode.List
                        viewModel.clearDepartureOutcome()
                    },
                    onConfirm = { notes ->
                        viewModel.recordDeparture(mode.visit.id, notes)
                        screenMode = VisitsScreenMode.List
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
    dark: Boolean,
    onMenuClick: () -> Unit,
    onLogArrivalClick: () -> Unit,
    onVisitClick: (TruckVisit) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onTypeFilterSelected: (VisitType?) -> Unit,
    onTriggerSync: () -> Unit,
    onDismissBanner: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        floatingActionButton = {
            LiquidArrivalFab(
                onClick = onLogArrivalClick,
                dark = dark,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.End,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // Liquid Glass Header Strip
            LiquidVisitsHeader(
                onMenuClick = onMenuClick,
                dark = dark,
            )

            // Yard Capacity & Status Metrics Strip
            YardMetricsBar(
                openVisits = uiState.openVisits,
                dark = dark,
            )

            // Frosted Glass Search & Filter Controls
            LiquidSearchAndFilterBar(
                searchQuery = uiState.searchQuery,
                onQueryChange = onSearchQueryChanged,
                selectedFilter = uiState.selectedTypeFilter,
                onSelectFilter = onTypeFilterSelected,
                dark = dark,
            )

            // Active Visits Scrollable List
            if (uiState.filteredVisits.isEmpty()) {
                LiquidEmptyVisitsState(
                    isFiltered = uiState.searchQuery.isNotBlank() || uiState.selectedTypeFilter != null,
                    dark = dark,
                    onLogArrivalClick = onLogArrivalClick,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.filteredVisits, key = { it.id }) { visit ->
                        LiquidActiveVisitCard(
                            visit = visit,
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
    onBack: () -> Unit,
    onConfirm: (draft: ArrivalDraft, allowDuplicate: Boolean) -> Unit,
    dark: Boolean,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors

    // 3 MANDATORY (NON-OPTIONAL) FIELDS:
    var countryCodeInput by remember { mutableStateOf("ZM") }
    var frontPlateInput by remember { mutableStateOf("") }
    var horsePlateInput by remember { mutableStateOf("") }

    // TRANSPORTER / CARRIER FIELD WITH SEPARATE LIST & DROPDOWN:
    var transporterInput by remember { mutableStateOf("") }
    var isTransporterFocused by remember { mutableStateOf(false) }

    // LIVE OCR SCANNER MODAL STATE:
    var activeScannerTarget by remember { mutableStateOf<String?>(null) } // "FRONT", "HORSE", or "TRANSPORTER"

    // OPTIONAL FIELDS:
    var selectedType by remember { mutableStateOf(VisitType.Quick) } // Quick is the first category
    var customPurposeInput by remember { mutableStateOf("") }
    var driverNameInput by remember { mutableStateOf("") }
    var driverPhoneDigits by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    val cleanCountry = countryCodeInput.trim().uppercase().take(4)
    val cleanFrontPlate = normalizePlate(frontPlateInput)
    val cleanHorsePlate = normalizePlate(horsePlateInput)

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
    val canSave = cleanCountry.isNotBlank() &&
        cleanFrontPlate.isNotBlank() &&
        cleanHorsePlate.isNotBlank() &&
        isPhoneValid

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    imageVector = Icons.Default.ArrowBack,
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

                    // Field 1: Country Code
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
                        LiquidTextInput(
                            value = countryCodeInput,
                            onValueChange = { countryCodeInput = it.take(4).uppercase() },
                            placeholder = "ZM",
                            isMonospace = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
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

                            // Camera Scan Trigger Button for Front Plate
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
                        LiquidTextInput(
                            value = frontPlateInput,
                            onValueChange = { frontPlateInput = it.take(20).uppercase() },
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

                            // Camera Scan Trigger Button for Horse Plate
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
                        LiquidTextInput(
                            value = horsePlateInput,
                            onValueChange = { horsePlateInput = it.take(20).uppercase() },
                            placeholder = stringResource(R.string.hint_horse_plate),
                            isMonospace = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            dark = dark,
                        )
                    }

                    // Real-Time Live Dual Plate Preview
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                                if (frontPlateInput.isEmpty()) frontPlateInput = plate
                                                else if (horsePlateInput.isEmpty()) horsePlateInput = plate
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

            // CARD 2: TRANSPORTER / CARRIER DETAILS (WITH AUTOCOMPLETE DROPDOWN)
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "TRANSPORTER / CARRIER (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = scheme.primary,
                            letterSpacing = 0.8.sp,
                        ),
                    )

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

                            // Camera Scan Trigger Button for Transporter Marking / Logo
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
                    }
                }
            }

            // CARD 3: VISIT PURPOSE (QUICK FIRST, WITH "OTHER" CUSTOM PURPOSE)
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "VISIT PURPOSE (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = scheme.primary,
                            letterSpacing = 0.8.sp,
                        ),
                    )

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
                }
            }

            // CARD 4: DRIVER & INTERNATIONAL PHONE (OPTIONAL)
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "DRIVER & INTERNATIONAL CONTACT (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = scheme.primary,
                            letterSpacing = 0.8.sp,
                        ),
                    )

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
                }
            }

            // CARD 5: INSPECTION AND CARGO NOTES IF AVAILABLE (OPTIONAL)
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.lbl_arrival_notes),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = scheme.primary,
                            letterSpacing = 0.8.sp,
                        ),
                    )
                    LiquidTextInput(
                        value = notesInput,
                        onValueChange = { notesInput = it.take(300) },
                        placeholder = stringResource(R.string.hint_arrival_notes),
                        maxLines = 3,
                        dark = dark,
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
                    text = stringResource(R.string.btn_confirm_arrival),
                    enabled = canSave,
                    dark = dark,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // Persist transporter to local separate store for future autocomplete
                        if (transporterInput.isNotBlank()) {
                            TransporterStore.addTransporter(context, transporterInput)
                        }

                        val fullPhone = if (driverPhoneDigits.isNotEmpty()) "+$driverPhoneDigits" else null
                        val purposeTag = if (selectedType == VisitType.Other && customPurposeInput.isNotBlank()) {
                            "[Purpose: ${customPurposeInput.trim()}] "
                        } else ""
                        val transporterTag = if (transporterInput.isNotBlank()) {
                            "[Transporter: ${transporterInput.trim()}] "
                        } else ""
                        val combinedNotes = "[Country: $cleanCountry] [Front: $cleanFrontPlate] [Horse: $cleanHorsePlate] $transporterTag$purposeTag${notesInput.trim()}".trim()

                        val draft = ArrivalDraft(
                            plateNumber = cleanFrontPlate.take(20),
                            visitType = selectedType,
                            driverName = driverNameInput.ifBlank { null },
                            driverPhone = fullPhone,
                            notes = combinedNotes.ifBlank { null },
                        )
                        onConfirm(draft, true)
                    },
                )

                // Cancel button at the bottom
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

            Spacer(Modifier.height(80.dp))
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
                        frontPlateInput = scannedValue
                    }
                    "HORSE" -> {
                        horsePlateInput = scannedValue
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
    onBack: () -> Unit,
    onConfirm: (notes: String?) -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    var departureNotes by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val plateInfo = remember(visit.plateNumber, visit.arrivalNotes) {
        extractPlateInfo(visit.plateNumber, visit.arrivalNotes)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column {
                Text(
                    text = stringResource(R.string.dialog_departure_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onSurface,
                        letterSpacing = (-0.5).sp,
                    ),
                )
                Text(
                    text = "Confirm vehicle clearance & departure",
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
            // CARD 1: VEHICLE SUMMARY
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InternationalPlateBadge(
                            countryCode = plateInfo.country,
                            plateNumber = plateInfo.frontPlate,
                            plateLabel = "FRONT",
                            dark = dark,
                        )
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

                    if (plateInfo.horsePlate != plateInfo.frontPlate) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Horse Plate:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                            InternationalPlateBadge(
                                countryCode = plateInfo.country,
                                plateNumber = plateInfo.horsePlate,
                                plateLabel = "HORSE",
                                dark = dark,
                            )
                        }
                    }

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

                    visit.reference?.let { ref ->
                        Text(
                            text = "Reference Token: #$ref",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant,
                            ),
                        )
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
                                    text = "Total Time On Yard",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSurfaceVariant,
                                    ),
                                )
                            }
                            Text(
                                text = formatElapsedDuration(visit.arrivedAt),
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
                }
            }

            // CARD 2: INSPECTION AND DEPARTURE NOTES IF AVAILABLE
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.lbl_departure_notes),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = scheme.primary,
                            letterSpacing = 0.8.sp,
                        ),
                    )
                    LiquidTextInput(
                        value = departureNotes,
                        onValueChange = { departureNotes = it.take(300) },
                        placeholder = stringResource(R.string.hint_departure_notes),
                        maxLines = 3,
                        dark = dark,
                    )
                }
            }

            // BOTTOM ACTIONS: Confirm Departure on top, Cancel at the bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Confirm Departure button on top
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
                        .clickable { showConfirmDialog = true }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
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

                // Cancel button at the bottom
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

            Spacer(Modifier.height(80.dp))
        }
    }

    // Confirmation Modal Dialog
    if (showConfirmDialog) {
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
                                imageVector = Icons.Default.Logout,
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
                                    text = "Token: #$ref  •  Stay: ${formatElapsedDuration(visit.arrivedAt)}",
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
                            // Confirm Departure button on top
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
                                    .clickable {
                                        showConfirmDialog = false
                                        onConfirm(departureNotes.ifBlank { null })
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

                            // Cancel button at the bottom
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
}

// =============================================================================
// REUSABLE LIQUID GLASS COMPONENTS
// =============================================================================

@Composable
private fun LiquidVisitsHeader(
    onMenuClick: () -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(amberAccent.copy(alpha = if (dark) 0.14f else 0.10f))
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    amberAccent.copy(alpha = 0.6f),
                                    amberAccent.copy(alpha = 0.2f),
                                ),
                            ),
                        ),
                        RoundedCornerShape(16.dp),
                    )
                    .drawBehind {
                        drawRoundRect(
                            Brush.verticalGradient(
                                listOf(
                                    amberAccent.copy(alpha = 0.15f),
                                    Color.Transparent,
                                ),
                            ),
                            size = Size(size.width, 2.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(amberAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = amberAccent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.lbl_pending_sync, pendingCount),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                ),
                            )
                            Text(
                                text = stringResource(R.string.lbl_offline_local),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(CapsuleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFFF59E0B),
                                        Color(0xFFD97706),
                                    ),
                                ),
                            )
                            .clickable(
                                enabled = !isSyncing,
                                onClick = onSyncClick,
                            )
                            .drawBehind {
                                drawRoundRect(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.45f),
                                            Color.Transparent,
                                        ),
                                    ),
                                    size = Size(size.width, 2.dp.toPx()),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(999.dp.toPx()),
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
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
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.3.sp,
                                    ),
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceVariant.copy(alpha = if (dark) 0.5f else 0.7f))
                    .border(
                        BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = bannerMessage,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
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
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidSearchAndFilterBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    selectedFilter: VisitType?,
    onSelectFilter: (VisitType?) -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = TruckParkExtras.colors
    var isFocused by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    modifier = Modifier.weight(1f),
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
    onCardClick: () -> Unit,
    dark: Boolean,
) {
    val scheme = MaterialTheme.colorScheme

    val plateInfo = remember(visit.plateNumber, visit.arrivalNotes) {
        extractPlateInfo(visit.plateNumber, visit.arrivalNotes)
    }

    val typeColor = when (visit.visitType) {
        VisitType.Quick -> Color(0xFF10B981)
        VisitType.Overnight -> Color(0xFF6366F1)
        VisitType.Transit -> Color(0xFF8B5CF6)
        VisitType.Loading -> Color(0xFFF59E0B)
        VisitType.Other -> scheme.onSurfaceVariant
    }

    val displayNotes = cleanDisplayNotes(visit.arrivalNotes)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCardClick,
            ),
        contentPadding = 16,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Row 1: Stacked International Front & Horse Plate Badges + Visit Type Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // Dual Plates: Front on top, Horse down below
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Front Plate Badge
                    InternationalPlateBadge(
                        countryCode = plateInfo.country,
                        plateNumber = plateInfo.frontPlate,
                        plateLabel = "FRONT",
                        dark = dark,
                    )

                    // Horse Plate Badge if different: placed underneath Front plate
                    if (plateInfo.horsePlate != plateInfo.frontPlate) {
                        InternationalPlateBadge(
                            countryCode = plateInfo.country,
                            plateNumber = plateInfo.horsePlate,
                            plateLabel = "HORSE",
                            dark = dark,
                        )
                    }
                }

                // Visit Type Glass Capsule
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
                            text = plateInfo.customPurpose ?: visit.visitType.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = typeColor,
                            ),
                        )
                    }
                }
            }

            // Row 1.5: Transporter Badge if present
            plateInfo.transporter?.let { carrier ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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

            // Row 4: Elapsed Duration + Depart Button
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
                        tint = scheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = formatElapsedDuration(visit.arrivedAt),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = scheme.onSurface,
                        ),
                    )
                    visit.reference?.let { ref ->
                        Text(
                            text = "· #$ref",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = scheme.onSurfaceVariant,
                            ),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
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
            // Dynamic Country strip (e.g. ZM, ZA, TZ, CD, ZW)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF059669))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = countryCode.ifBlank { "ZM" },
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
                imageVector = Icons.Default.Logout,
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
private fun LiquidTextInput(
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
private fun LiquidInternationalPhoneInput(
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
private fun LiquidActionButton(
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
            .clip(CapsuleShape)
            .background(if (enabled) activeBrush else disabledBrush)
            .border(borderStroke, CapsuleShape)
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
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(999.dp.toPx()),
                    )
                }
            }
            .padding(vertical = 14.dp),
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
    dark: Boolean,
    onLogArrivalClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

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
                    text = if (isFiltered) "No Matching Trucks" else stringResource(R.string.empty_visits_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onSurface,
                    ),
                )

                Text(
                    text = if (isFiltered) "Try adjusting your search query or switching the visit type filter."
                    else stringResource(R.string.empty_visits_subtitle),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                if (!isFiltered) {
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

private fun formatElapsedDuration(arrivedAt: Instant): String {
    val duration = Duration.between(arrivedAt, Instant.now())
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return when {
        hours <= 0 && minutes <= 0 -> "Just now"
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
