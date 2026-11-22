package com.solerunner.myhealth.ui.doctor

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solerunner.myhealth.data.loadSnapshot
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.analytics.DoctorSummaryBuilder
import com.solerunner.myhealth.domain.analytics.FocusEngine
import com.solerunner.myhealth.domain.analytics.FocusInput
import com.solerunner.myhealth.domain.model.DoctorSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DoctorSummaryUiState(
    val loading: Boolean = true,
    val summary: DoctorSummary? = null,
    val shareIntent: Intent? = null,
    val error: String? = null,
)

class DoctorSummaryViewModel(private val container: AppContainer) : ViewModel() {

    private val pdf = container.doctorSummaryPdf

    private val _state = MutableStateFlow(DoctorSummaryUiState())
    val state: StateFlow<DoctorSummaryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        runCatching {
            val snapshot = container.loadSnapshot()
            val focus = FocusEngine.analyse(
                FocusInput(
                    today = snapshot.today,
                    profile = snapshot.profile,
                    series = snapshot.series,
                    measurements = snapshot.measurements,
                    medicines = snapshot.medicines,
                    treatments = snapshot.treatments,
                    dailyMetrics = snapshot.dailyMetrics,
                ),
            )
            val deviceLines = listOfNotNull(
                snapshot.dailyMetrics.mapNotNull { it.sleepMinutes }.takeIf { it.isNotEmpty() }?.let {
                    com.solerunner.myhealth.domain.model.SummaryLine(
                        label = "Sleep",
                        value = "%.1f h".format(it.average() / 60.0),
                    )
                },
                snapshot.dailyMetrics.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.let {
                    com.solerunner.myhealth.domain.model.SummaryLine(
                        label = "Steps",
                        value = "%.0f / day".format(it.average()),
                    )
                },
            )
            DoctorSummaryBuilder.build(
                today = snapshot.today,
                since = DoctorSummaryBuilder.windowStart(snapshot.today),
                series = snapshot.series,
                focus = focus,
                medicines = snapshot.activeMedicines.map { it.name + (it.strength?.let { s -> " $s" } ?: "") },
                treatments = snapshot.treatments.mapNotNull { treatment ->
                    treatment.date?.let { treatment.name + " (" + it + ")" }
                },
                deviceLines = deviceLines,
            )
        }.onSuccess { summary -> _state.value = DoctorSummaryUiState(loading = false, summary = summary) }
            .onFailure { error -> _state.value = DoctorSummaryUiState(loading = false, error = error.message) }
    }

    fun generatePdf() {
        val summary = _state.value.summary ?: return
        viewModelScope.launch {
            runCatching {
                val file = pdf.write(summary, summaryPatientName())
                container.exporter.shareIntent(file, "application/pdf")
            }.onSuccess { intent -> _state.update { it.copy(shareIntent = intent) } }
                .onFailure { error -> _state.update { it.copy(error = error.message) } }
        }
    }

    private suspend fun summaryPatientName(): String = container.profiles.current()?.displayName.orEmpty()

    fun clearShare() = _state.update { it.copy(shareIntent = null) }
}
