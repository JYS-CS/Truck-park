package com.truckpark.management.feature.visits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.truckpark.management.app.TruckParkApplication
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.data.local.db.SyncQueueEntity
import com.truckpark.management.domain.models.ArrivalDraft
import com.truckpark.management.domain.models.ArrivalOutcome
import com.truckpark.management.domain.models.SyncAlertBanner
import com.truckpark.management.domain.models.SyncAlertSeverity
import com.truckpark.management.domain.models.SyncState
import com.truckpark.management.domain.models.TruckVisit
import com.truckpark.management.domain.models.VisitCorrectionDraft
import com.truckpark.management.domain.models.VisitCorrectionRecord
import com.truckpark.management.domain.models.VisitSeal
import com.truckpark.management.domain.models.VisitType
import com.truckpark.management.domain.models.cleanDisplayNotes
import com.truckpark.management.domain.models.extractPlateInfo
import com.truckpark.management.domain.repositories.VisitsRepository
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VisitsUiState(
    val openVisits: List<TruckVisit> = emptyList(),
    val recentCompleted: List<TruckVisit> = emptyList(),
    val filteredVisits: List<TruckVisit> = emptyList(),
    val showCompleted: Boolean = false,
    val pendingSyncCount: Int = 0,
    val searchQuery: String = "",
    val selectedTypeFilter: VisitType? = null,
    val isSyncing: Boolean = false,
    val lastSyncedAt: Instant? = null,
    val syncAlertBanner: SyncAlertBanner? = null,
    val conflictVisits: List<TruckVisit> = emptyList(),
    val isSubmittingArrival: Boolean = false,
    val arrivalOutcome: ArrivalOutcome? = null,
    val isSubmittingDeparture: Boolean = false,
    val departureOutcome: AuthResult<TruckVisit>? = null,
    val isSubmittingCorrection: Boolean = false,
    val correctionOutcome: AuthResult<TruckVisit>? = null,
    val plateSuggestions: List<String> = emptyList(),
)

private data class DataSnapshot(
    val open: List<TruckVisit>,
    val recent: List<TruckVisit>,
    val pendingCount: Int,
    val failingOps: List<SyncQueueEntity>,
    val failedOrConflict: List<TruckVisit>,
    val lastSyncedAt: Instant?,
)

private data class FilterState(
    val query: String,
    val typeFilter: VisitType?,
    val showCompleted: Boolean,
    val suggestions: List<String>,
)

private data class ActionState(
    val isSyncing: Boolean,
    val sessionExpired: Boolean,
    val manualFeedback: SyncAlertBanner?,
    val isSubmittingArrival: Boolean,
    val arrivalOutcome: ArrivalOutcome?,
    val isSubmittingDeparture: Boolean,
    val departureOutcome: AuthResult<TruckVisit>?,
    val isSubmittingCorrection: Boolean,
    val correctionOutcome: AuthResult<TruckVisit>?,
)

class VisitsViewModel(
    private val repository: VisitsRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedTypeFilter = MutableStateFlow<VisitType?>(null)
    private val _showCompleted = MutableStateFlow(false)
    private val _plateSuggestions = MutableStateFlow<List<String>>(emptyList())

    private val _isSyncing = MutableStateFlow(false)
    private val _sessionExpired = MutableStateFlow(false)
    private val _manualFeedback = MutableStateFlow<SyncAlertBanner?>(null)
    private val _bannerDismissed = MutableStateFlow(false)

    private val _isSubmittingArrival = MutableStateFlow(false)
    private val _arrivalOutcome = MutableStateFlow<ArrivalOutcome?>(null)

    private val _isSubmittingDeparture = MutableStateFlow(false)
    private val _departureOutcome = MutableStateFlow<AuthResult<TruckVisit>?>(null)

    private val _isSubmittingCorrection = MutableStateFlow(false)
    private val _correctionOutcome = MutableStateFlow<AuthResult<TruckVisit>?>(null)

    private val dataSnapshotFlow: Flow<DataSnapshot> = combine(
        repository.observeOpenVisits(),
        repository.observeRecentCompleted(),
        repository.observePendingCount(),
        repository.observeFailingSyncOps(FAIL_ALERT_THRESHOLD),
        repository.observeFailedAndConflict(),
    ) { open, recent, pendingCount, failingOps, failedOrConflict ->
        DataSnapshot(
            open = open,
            recent = recent,
            pendingCount = pendingCount,
            failingOps = failingOps,
            failedOrConflict = failedOrConflict,
            lastSyncedAt = null,
        )
    }.combine(repository.observeLastSyncedAt()) { snapshot, lastSynced ->
        snapshot.copy(lastSyncedAt = lastSynced)
    }

    private val filterStateFlow: Flow<FilterState> = combine(
        _searchQuery,
        _selectedTypeFilter,
        _showCompleted,
        _plateSuggestions,
    ) { query, typeFilter, showCompleted, suggestions ->
        FilterState(query, typeFilter, showCompleted, suggestions)
    }

    private val syncStatusFlow = combine(
        _isSyncing,
        _sessionExpired,
        _manualFeedback,
    ) { syncing, expired, feedback -> Triple(syncing, expired, feedback) }

    private val arrivalSubmitFlow = combine(
        _isSubmittingArrival,
        _arrivalOutcome,
    ) { busy, outcome -> busy to outcome }

    private val departureSubmitFlow = combine(
        _isSubmittingDeparture,
        _departureOutcome,
    ) { busy, outcome -> busy to outcome }

    private val correctionSubmitFlow = combine(
        _isSubmittingCorrection,
        _correctionOutcome,
    ) { busy, outcome -> busy to outcome }

    private val actionStateFlow: Flow<ActionState> = combine(
        syncStatusFlow,
        arrivalSubmitFlow,
        departureSubmitFlow,
        correctionSubmitFlow,
    ) { syncStatus, arrival, departure, correction ->
        ActionState(
            isSyncing = syncStatus.first,
            sessionExpired = syncStatus.second,
            manualFeedback = syncStatus.third,
            isSubmittingArrival = arrival.first,
            arrivalOutcome = arrival.second,
            isSubmittingDeparture = departure.first,
            departureOutcome = departure.second,
            isSubmittingCorrection = correction.first,
            correctionOutcome = correction.second,
        )
    }

    val uiState: StateFlow<VisitsUiState> = combine(
        dataSnapshotFlow,
        filterStateFlow,
        actionStateFlow,
        _bannerDismissed,
    ) { data, filters, actions, dismissed ->
        val sourceList = if (filters.showCompleted) data.recent else data.open
        val cleanQuery = filters.query.trim().uppercase()
        val filtered = sourceList.filter { visit ->
            val matchesType = filters.typeFilter == null || visit.visitType == filters.typeFilter
            matchesType && (cleanQuery.isEmpty() || matchesSearch(visit, cleanQuery))
        }

        val banner = if (dismissed && actions.manualFeedback == null) {
            null
        } else {
            buildSyncBanner(
                sessionExpired = actions.sessionExpired,
                failingOps = data.failingOps,
                failedOrConflict = data.failedOrConflict,
                manualFeedback = actions.manualFeedback,
            )
        }

        VisitsUiState(
            openVisits = data.open,
            recentCompleted = data.recent,
            filteredVisits = filtered,
            showCompleted = filters.showCompleted,
            pendingSyncCount = data.pendingCount,
            searchQuery = filters.query,
            selectedTypeFilter = filters.typeFilter,
            isSyncing = actions.isSyncing,
            lastSyncedAt = data.lastSyncedAt,
            syncAlertBanner = banner,
            conflictVisits = data.failedOrConflict.filter { it.syncState == SyncState.Conflict },
            isSubmittingArrival = actions.isSubmittingArrival,
            arrivalOutcome = actions.arrivalOutcome,
            isSubmittingDeparture = actions.isSubmittingDeparture,
            departureOutcome = actions.departureOutcome,
            isSubmittingCorrection = actions.isSubmittingCorrection,
            correctionOutcome = actions.correctionOutcome,
            plateSuggestions = filters.suggestions,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        VisitsUiState(),
    )

    init {
        // Silent auto-sync on startup and periodically every 20 seconds
        syncAndRefresh(silent = true)
        startPeriodicAutoSync()
    }

    private fun startPeriodicAutoSync() {
        viewModelScope.launch {
            while (isActive) {
                delay(20_000)
                if (!_isSyncing.value) {
                    try {
                        val report = repository.sync()
                        if (report.signedOut) {
                            _sessionExpired.value = true
                        } else if (!report.offline) {
                            _sessionExpired.value = false
                            repository.refresh()
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun observeVisitHistory(visitId: String): Flow<List<VisitCorrectionRecord>> =
        repository.observeVisitHistory(visitId)

    fun refreshVisitHistory(visitId: String) {
        viewModelScope.launch {
            try {
                repository.refreshVisitHistory(visitId)
            } catch (_: Exception) {
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onPlateInputChanged(prefix: String) {
        viewModelScope.launch {
            _plateSuggestions.value = repository.suggestPlates(prefix)
        }
    }

    fun onShowCompletedChanged(showCompleted: Boolean) {
        _showCompleted.value = showCompleted
    }

    fun onTypeFilterSelected(type: VisitType?) {
        _selectedTypeFilter.value = if (_selectedTypeFilter.value == type) null else type
    }

    fun recordArrival(draft: ArrivalDraft, allowDuplicate: Boolean = false) {
        if (_isSubmittingArrival.value) return
        viewModelScope.launch {
            _isSubmittingArrival.value = true
            val outcome = repository.recordArrival(draft, allowDuplicate)
            _isSubmittingArrival.value = false
            _arrivalOutcome.value = outcome
            if (outcome is ArrivalOutcome.Saved) {
                triggerSync(silent = true)
            }
        }
    }

    fun recordDeparture(
        visitId: String,
        notes: String?,
        updatedSeals: List<VisitSeal>? = null,
    ) {
        if (_isSubmittingDeparture.value) return
        viewModelScope.launch {
            _isSubmittingDeparture.value = true
            val result = repository.recordDeparture(visitId, notes, updatedSeals)
            _isSubmittingDeparture.value = false
            _departureOutcome.value = result
            if (result is AuthResult.Success) {
                triggerSync(silent = true)
            }
        }
    }

    fun recordCorrection(
        draft: VisitCorrectionDraft,
        canCorrectCritical: Boolean,
        editorName: String?,
    ) {
        if (_isSubmittingCorrection.value) return
        viewModelScope.launch {
            _isSubmittingCorrection.value = true
            val result = repository.recordCorrection(draft, canCorrectCritical, editorName)
            _isSubmittingCorrection.value = false
            _correctionOutcome.value = result
            if (result is AuthResult.Success) {
                triggerSync(silent = true)
            }
        }
    }

    fun resolveConflictKeepLocal(visitId: String, onDone: (AuthResult<TruckVisit>) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repository.resolveConflictKeepLocal(visitId)
            _isSyncing.value = false
            if (result is AuthResult.Success) {
                _manualFeedback.value = SyncAlertBanner(
                    message = "Kept your handset record and synced it to the cloud.",
                    severity = SyncAlertSeverity.Info,
                    showRetry = false,
                )
                scheduleAutoClearFeedback()
            }
            onDone(result)
        }
    }

    fun resolveConflictUseServer(visitId: String, onDone: (AuthResult<Unit>) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repository.resolveConflictUseServer(visitId)
            _isSyncing.value = false
            if (result is AuthResult.Success) {
                _manualFeedback.value = SyncAlertBanner(
                    message = "Updated visit from the cloud record.",
                    severity = SyncAlertSeverity.Info,
                    showRetry = false,
                )
                scheduleAutoClearFeedback()
            }
            onDone(result)
        }
    }

    fun triggerSync(silent: Boolean = true) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val report = repository.sync()
            _isSyncing.value = false
            handleSyncReport(report, silent = silent)
        }
    }

    fun syncAndRefresh(silent: Boolean = true) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val report = repository.sync()
            if (!report.signedOut && !report.offline) {
                repository.refresh()
            }
            _isSyncing.value = false
            handleSyncReport(report, silent = silent)
        }
    }

    fun manualRetrySync() {
        if (_isSyncing.value) return
        _bannerDismissed.value = false
        viewModelScope.launch {
            _isSyncing.value = true
            repository.retryFailed()
            val report = repository.sync()
            if (!report.signedOut && !report.offline) {
                repository.refresh()
            }
            _isSyncing.value = false
            handleSyncReport(report, silent = false)
        }
    }

    private fun handleSyncReport(report: com.truckpark.management.domain.models.SyncReport, silent: Boolean) {
        if (report.signedOut) {
            _sessionExpired.value = true
            return
        }
        _sessionExpired.value = false
        if (!silent) {
            val feedback = when {
                report.offline -> SyncAlertBanner(
                    message = "Still offline — check your internet connection and tap Retry when back online.",
                    severity = SyncAlertSeverity.Warning,
                    showRetry = true,
                )
                report.conflicts > 0 -> SyncAlertBanner(
                    message = "${report.conflicts} visit(s) conflict with another device and need Supervisor or Admin review.",
                    severity = SyncAlertSeverity.Warning,
                    showRetry = false,
                    needsConflictReview = true,
                )
                report.failed > 0 -> SyncAlertBanner(
                    message = "Could not upload ${report.failed} item(s)${report.lastErrorMessage?.let { ": $it" } ?: "."}",
                    severity = SyncAlertSeverity.Error,
                    showRetry = true,
                )
                report.synced > 0 -> SyncAlertBanner(
                    message = "Synced ${report.synced} visit update(s) to the cloud.",
                    severity = SyncAlertSeverity.Info,
                    showRetry = false,
                )
                else -> SyncAlertBanner(
                    message = "Everything is up to date.",
                    severity = SyncAlertSeverity.Info,
                    showRetry = false,
                )
            }
            _manualFeedback.value = feedback
            if (feedback.severity == SyncAlertSeverity.Info) {
                scheduleAutoClearFeedback()
            }
        }
    }

    private fun scheduleAutoClearFeedback() {
        viewModelScope.launch {
            delay(3500)
            if (_manualFeedback.value?.severity == SyncAlertSeverity.Info) {
                _manualFeedback.value = null
            }
        }
    }

    fun clearArrivalOutcome() {
        _arrivalOutcome.value = null
    }

    fun clearDepartureOutcome() {
        _departureOutcome.value = null
    }

    fun clearCorrectionOutcome() {
        _correctionOutcome.value = null
    }

    fun dismissBanner() {
        _manualFeedback.value = null
        _bannerDismissed.value = true
    }

    private fun buildSyncBanner(
        sessionExpired: Boolean,
        failingOps: List<SyncQueueEntity>,
        failedOrConflict: List<TruckVisit>,
        manualFeedback: SyncAlertBanner?,
    ): SyncAlertBanner? {
        if (manualFeedback != null) return manualFeedback
        if (sessionExpired) {
            return SyncAlertBanner(
                message = "Your sign-in session expired. Sign in again so pending visits can upload.",
                severity = SyncAlertSeverity.Error,
                showRetry = false,
                requiresSignIn = true,
            )
        }

        val conflicts = failedOrConflict.filter { it.syncState == SyncState.Conflict }
        if (conflicts.isNotEmpty()) {
            return SyncAlertBanner(
                message = "${conflicts.size} visit(s) conflict with another device's record and need Supervisor/Admin review.",
                severity = SyncAlertSeverity.Warning,
                showRetry = false,
                needsConflictReview = true,
            )
        }

        val failedVisits = failedOrConflict.filter { it.syncState == SyncState.Failed }
        if (failedVisits.isNotEmpty()) {
            val reason = failedVisits.firstOrNull()?.syncError?.takeIf { it.isNotBlank() }
                ?: "rejected by server"
            return SyncAlertBanner(
                message = "${failedVisits.size} visit update(s) failed to upload ($reason). Review or tap Retry.",
                severity = SyncAlertSeverity.Error,
                showRetry = true,
            )
        }

        // Auto-sync stays invisible until an operation fails repeatedly (>= FAIL_ALERT_THRESHOLD tries)
        if (failingOps.isNotEmpty()) {
            val lastErr = failingOps.lastOrNull()?.lastError.orEmpty()
            val isNetwork = lastErr.contains("connection", ignoreCase = true) ||
                lastErr.contains("reach the server", ignoreCase = true) ||
                lastErr.contains("offline", ignoreCase = true)
            val msg = if (isNetwork) {
                "Still offline — ${failingOps.size} visit update(s) couldn't upload after several tries. Check your internet and tap Retry."
            } else {
                "Auto-sync failed several times (${failingOps.size} pending). ${lastErr.ifBlank { "Tap Retry to try again." }}"
            }
            return SyncAlertBanner(
                message = msg,
                severity = SyncAlertSeverity.Warning,
                showRetry = true,
            )
        }

        return null
    }

    private fun matchesSearch(visit: TruckVisit, cleanQuery: String): Boolean {
        val info = extractPlateInfo(visit.plateNumber, visit.arrivalNotes, visit.departureNotes)
        val notes = cleanDisplayNotes(visit.arrivalNotes)?.uppercase().orEmpty()
        val depNotes = cleanDisplayNotes(visit.departureNotes)?.uppercase().orEmpty()
        return visit.plateNumber.uppercase().contains(cleanQuery) ||
            info.frontPlate.uppercase().contains(cleanQuery) ||
            info.horsePlate.uppercase().contains(cleanQuery) ||
            info.country.uppercase().contains(cleanQuery) ||
            (info.customCountryReason?.uppercase()?.contains(cleanQuery) == true) ||
            (info.transporter?.uppercase()?.contains(cleanQuery) == true) ||
            (info.customPurpose?.uppercase()?.contains(cleanQuery) == true) ||
            (info.exitVerifiedPlate?.uppercase()?.contains(cleanQuery) == true) ||
            info.customFields.any {
                it.columnName.uppercase().contains(cleanQuery) ||
                    it.value.uppercase().contains(cleanQuery)
            } ||
            (visit.reference?.uppercase()?.contains(cleanQuery) == true) ||
            (visit.driverName?.uppercase()?.contains(cleanQuery) == true) ||
            (visit.driverPhone?.uppercase()?.contains(cleanQuery) == true) ||
            (visit.cargoDescription?.uppercase()?.contains(cleanQuery) == true) ||
            visit.cargoItems.any {
                it.description.uppercase().contains(cleanQuery) ||
                    (it.notes?.uppercase()?.contains(cleanQuery) == true)
            } ||
            visit.seals.any {
                it.sealNumber.uppercase().contains(cleanQuery) ||
                    it.sealType.uppercase().contains(cleanQuery)
            } ||
            notes.contains(cleanQuery) ||
            depNotes.contains(cleanQuery)
    }

    companion object {
        private const val FAIL_ALERT_THRESHOLD = 3

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruckParkApplication
                VisitsViewModel(app.container.visitsRepository)
            }
        }
    }
}

