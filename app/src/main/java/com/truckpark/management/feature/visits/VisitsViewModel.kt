package com.truckpark.management.feature.visits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.truckpark.management.app.TruckParkApplication
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.domain.models.ArrivalDraft
import com.truckpark.management.domain.models.ArrivalOutcome
import com.truckpark.management.domain.models.SyncReport
import com.truckpark.management.domain.models.TruckVisit
import com.truckpark.management.domain.models.VisitType
import com.truckpark.management.domain.models.normalizePlate
import com.truckpark.management.domain.repositories.VisitsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VisitsUiState(
    val openVisits: List<TruckVisit> = emptyList(),
    val filteredVisits: List<TruckVisit> = emptyList(),
    val recentCompleted: List<TruckVisit> = emptyList(),
    val pendingSyncCount: Int = 0,
    val searchQuery: String = "",
    val selectedTypeFilter: VisitType? = null,
    val isSyncing: Boolean = false,
    val syncBannerMessage: String? = null,
    val arrivalOutcome: ArrivalOutcome? = null,
    val departureOutcome: AuthResult<TruckVisit>? = null,
    val plateSuggestions: List<String> = emptyList(),
)

class VisitsViewModel(
    private val repository: VisitsRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow<VisitType?>(null)
    val selectedTypeFilter: StateFlow<VisitType?> = _selectedTypeFilter.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncBannerMessage = MutableStateFlow<String?>(null)
    val syncBannerMessage: StateFlow<String?> = _syncBannerMessage.asStateFlow()

    private val _arrivalOutcome = MutableStateFlow<ArrivalOutcome?>(null)
    val arrivalOutcome: StateFlow<ArrivalOutcome?> = _arrivalOutcome.asStateFlow()

    private val _departureOutcome = MutableStateFlow<AuthResult<TruckVisit>?>(null)
    val departureOutcome: StateFlow<AuthResult<TruckVisit>?> = _departureOutcome.asStateFlow()

    private val _plateSuggestions = MutableStateFlow<List<String>>(emptyList())
    val plateSuggestions: StateFlow<List<String>> = _plateSuggestions.asStateFlow()

    val openVisits: StateFlow<List<TruckVisit>> = repository.observeOpenVisits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentCompleted: StateFlow<List<TruckVisit>> = repository.observeRecentCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingSyncCount: StateFlow<Int> = repository.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uiState: StateFlow<VisitsUiState> = combine(
        openVisits,
        recentCompleted,
        pendingSyncCount,
        _searchQuery,
        _selectedTypeFilter,
        _isSyncing,
        _syncBannerMessage,
        _arrivalOutcome,
        _departureOutcome,
        _plateSuggestions,
    ) { params: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val open = params[0] as List<TruckVisit>
        @Suppress("UNCHECKED_CAST")
        val recent = params[1] as List<TruckVisit>
        val pendingCount = params[2] as Int
        val query = params[3] as String
        val typeFilter = params[4] as VisitType?
        val syncing = params[5] as Boolean
        val message = params[6] as String?
        val arrival = params[7] as ArrivalOutcome?
        @Suppress("UNCHECKED_CAST")
        val departure = params[8] as AuthResult<TruckVisit>?
        @Suppress("UNCHECKED_CAST")
        val suggestions = params[9] as List<String>

        val cleanQuery = query.trim().uppercase()
        val filtered = open.filter { visit ->
            val matchesQuery = cleanQuery.isEmpty() ||
                visit.plateNumber.contains(cleanQuery) ||
                (visit.reference?.contains(cleanQuery) == true) ||
                (visit.driverName?.uppercase()?.contains(cleanQuery) == true)

            val matchesType = typeFilter == null || visit.visitType == typeFilter
            matchesQuery && matchesType
        }

        VisitsUiState(
            openVisits = open,
            filteredVisits = filtered,
            recentCompleted = recent,
            pendingSyncCount = pendingCount,
            searchQuery = query,
            selectedTypeFilter = typeFilter,
            isSyncing = syncing,
            syncBannerMessage = message,
            arrivalOutcome = arrival,
            departureOutcome = departure,
            plateSuggestions = suggestions,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        VisitsUiState(),
    )

    init {
        // Automatically attempt a background sync and refresh on screen startup silently
        syncAndRefresh(silent = true)
        startPeriodicAutoSync()
    }

    private fun startPeriodicAutoSync() {
        viewModelScope.launch {
            while (isActive) {
                delay(20_000) // Silent background synchronization every 20 seconds
                if (!_isSyncing.value) {
                    try {
                        repository.sync()
                        repository.refresh()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            val suggestions = repository.suggestPlates(query)
            _plateSuggestions.value = suggestions
        }
    }

    fun onTypeFilterSelected(type: VisitType?) {
        _selectedTypeFilter.value = if (_selectedTypeFilter.value == type) null else type
    }

    fun recordArrival(draft: ArrivalDraft, allowDuplicate: Boolean = true) {
        viewModelScope.launch {
            val outcome = repository.recordArrival(draft, allowDuplicate)
            _arrivalOutcome.value = outcome
            if (outcome is ArrivalOutcome.Saved) {
                // Trigger background silent sync immediately for this new record
                triggerSync(silent = true)
            }
        }
    }

    fun recordDeparture(visitId: String, notes: String?) {
        viewModelScope.launch {
            val result = repository.recordDeparture(visitId, notes)
            _departureOutcome.value = result
            if (result is AuthResult.Success) {
                triggerSync(silent = true)
            }
        }
    }

    fun triggerSync(silent: Boolean = true) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val report = repository.sync()
            _isSyncing.value = false
            if (!silent) {
                formatSyncMessage(report)
            } else if (report.signedOut) {
                _syncBannerMessage.value = "Session expired. Please sign in again to sync."
            }
        }
    }

    fun syncAndRefresh(silent: Boolean = true) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val report = repository.sync()
            repository.refresh()
            _isSyncing.value = false
            if (!silent) {
                formatSyncMessage(report)
            } else if (report.signedOut) {
                _syncBannerMessage.value = "Session expired. Please sign in again to sync."
            }
        }
    }

    fun retryFailed() {
        viewModelScope.launch {
            repository.retryFailed()
            triggerSync()
        }
    }

    fun clearArrivalOutcome() {
        _arrivalOutcome.value = null
    }

    fun clearDepartureOutcome() {
        _departureOutcome.value = null
    }

    fun dismissBanner() {
        _syncBannerMessage.value = null
    }

    private fun formatSyncMessage(report: SyncReport) {
        val msg = when {
            report.signedOut -> "Session expired. Please sign in again to sync."
            report.offline -> "Working offline. Saved locally, will sync when online."
            report.conflicts > 0 -> "${report.conflicts} conflict(s) flagged for Supervisor review."
            report.failed > 0 -> "${report.failed} item(s) could not sync. Check records."
            report.synced > 0 -> "Synced ${report.synced} visit update(s) to cloud."
            else -> null
        }
        _syncBannerMessage.value = msg
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruckParkApplication
                VisitsViewModel(app.container.visitsRepository)
            }
        }
    }
}

