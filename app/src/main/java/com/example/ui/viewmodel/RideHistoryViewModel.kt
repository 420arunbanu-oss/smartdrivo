package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PreferencesManager
import com.example.data.db.RideHistoryRepository
import com.example.data.db.entity.RideHistoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.TimeZone
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat

class RideHistoryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: RideHistoryRepository = RideHistoryRepository.getInstance(application)
    // RECOVER_TODAY_HISTORY_V1
    init {
        viewModelScope.launch {
            // HISTORY_PERSIST_ALL_DAYS_V2
            // History supports Today / Yesterday / Last 7 Days.
            // Opening History must never delete previous-day rows.
            // Live Room is authoritative. Restore old JSON only for a new DB;
            // never replay stale copies on every History screen opening.
            if (repository.getAllHistoryDirect().isEmpty()) {
                val legacyHistory = PreferencesManager(
                    getApplication<Application>()
                ).orderHistory.value
                if (legacyHistory.isNotEmpty()) {
                    repository.restoreHistoryItems(legacyHistory)
                }
            }

            // Convert any previously stuck PROCESSING records
            // into MISSED/IGNORED so History never stays pending.
            repository.finalizeAllStaleProcessing(
                timeoutMs = 8_000L
            )

            // PROCESSING_RECOVERY_LOOP_V2
            // Normal orders should finish immediately.
            // This is only a fallback for interrupted/abnormal cases.
            while (true) {
                delay(2_000L)

                repository.finalizeAllStaleProcessing(
                    timeoutMs = 8_000L
                )
            }
        }
    }
val history: StateFlow<List<RideHistoryEntity>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val latestRide: StateFlow<RideHistoryEntity?> = repository.latestRide
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val acceptedCount: StateFlow<Int> = repository.acceptedCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0
        )

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
