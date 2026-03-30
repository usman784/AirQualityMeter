package com.air.quality.meter.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.air.quality.meter.BuildConfig
import com.air.quality.meter.data.local.AppDatabase
import com.air.quality.meter.data.model.AQIRecord
import com.air.quality.meter.data.repository.AQIRepository
import com.air.quality.meter.util.AQIClassifier
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * ViewModel for the citizen AQI Dashboard (UC02).
 * Provides:
 *  - Live AQI fetch from OpenWeatherMap API
 *  - Offline fallback: last cached record from Room
 *  - Loading / error state
 */
class AQIDashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val dao        = AppDatabase.getInstance(app).aqiRecordDao()
    private val repository = AQIRepository(dao)
    private val uid        = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ── UI State ──────────────────────────────────────────────────────────────

    sealed class UiState {
        object Loading : UiState()
        data class Success(val record: AQIRecord, val category: AQIClassifier.AQICategory) : UiState()
        data class Offline(val record: AQIRecord, val category: AQIClassifier.AQICategory) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableLiveData<UiState>(UiState.Loading)
    val state: LiveData<UiState> = _state

    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Fetch live AQI for current device location.
     * Falls back to last cached record on network failure.
     */
    fun fetchAQI(lat: Double, lon: Double, locationName: String = "") {
        _state.value = UiState.Loading
        _isRefreshing.value = true
        viewModelScope.launch {
            val result = repository.fetchLiveAQI(
                uid          = uid,
                lat          = lat,
                lon          = lon,
                apiKey       = BuildConfig.OWM_API_KEY,
                locationName = locationName
            )
            result.fold(
                onSuccess = { record ->
                    val cat = AQIClassifier.classify(record.aqi)
                    _state.value = UiState.Success(record, cat)
                },
                onFailure = {
                    // Network failure — try Room cache
                    val cached = repository.getLatestRecord(uid)
                    if (cached != null) {
                        val cat = AQIClassifier.classify(cached.aqi)
                        _state.value = UiState.Offline(cached, cat)
                    } else {
                        _state.value = UiState.Error("Unable to fetch AQI. Check your internet connection.")
                    }
                }
            )
            _isRefreshing.value = false
        }
    }

    /** Load the most recent cached AQI record from Room (for immediate display while fetch runs) */
    fun loadCached() {
        viewModelScope.launch {
            val cached = repository.getLatestRecord(uid)
            if (cached != null && _state.value is UiState.Loading) {
                val cat = AQIClassifier.classify(cached.aqi)
                _state.value = UiState.Offline(cached, cat)
            }
        }
    }
}
