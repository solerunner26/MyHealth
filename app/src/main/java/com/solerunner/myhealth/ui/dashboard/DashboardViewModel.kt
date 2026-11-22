package com.solerunner.myhealth.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solerunner.myhealth.data.HealthSnapshot
import com.solerunner.myhealth.data.loadSnapshot
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.analytics.FocusEngine
import com.solerunner.myhealth.domain.analytics.FocusInput
import com.solerunner.myhealth.domain.model.FocusItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val snapshot: HealthSnapshot? = null,
    val focus: List<FocusItem> = emptyList(),
    val error: String? = null,
)

/**
 * The dashboard observes the database's change counter, so importing a report or logging a vital
 * updates the score, the focus list and the vitals tiles without any manual refresh.
 */
class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.database.changes().collect { refresh() }
        }
    }

    private suspend fun refresh() {
        runCatching {
            val snapshot = container.loadSnapshot()
            val settings = container.settings.settings.value
            val focus = FocusEngine.analyse(
                FocusInput(
                    today = snapshot.today,
                    profile = snapshot.profile,
                    series = snapshot.series,
                    measurements = snapshot.measurements,
                    medicines = snapshot.medicines,
                    treatments = snapshot.treatments,
                    dailyMetrics = snapshot.dailyMetrics,
                    healthConnectConnected = settings.healthConnectEnabled,
                    latestImportConfidence = snapshot.reports.firstOrNull()?.ocrConfidence,
                ),
            )
            _state.value = DashboardUiState(loading = false, snapshot = snapshot, focus = focus)
        }.onFailure { error ->
            _state.update { it.copy(loading = false, error = error.message) }
        }
    }
}
