package com.solerunner.myhealth.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.LabResult
import com.solerunner.myhealth.domain.model.Report
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportDetailUiState(
    val loading: Boolean = true,
    val report: Report? = null,
    val results: List<LabResult> = emptyList(),
    val deleted: Boolean = false,
    val error: String? = null,
)

class ReportDetailViewModel(
    private val container: AppContainer,
    private val reportId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportDetailUiState())
    val state: StateFlow<ReportDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            runCatching {
                val report = container.reports.byId(reportId)
                val results = container.reports.labResults(reportId)
                ReportDetailUiState(loading = false, report = report, results = results)
            }.onSuccess { _state.value = it }
                .onFailure { _state.value = ReportDetailUiState(loading = false, error = it.message) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching {
                container.reports.delete(reportId)
                container.audit.log("report.delete", "id=$reportId")
            }.onSuccess {
                _state.update { it.copy(deleted = true) }
            }
        }
    }
}

private fun MutableStateFlow<ReportDetailUiState>.update(transform: (ReportDetailUiState) -> ReportDetailUiState) {
    value = transform(value)
}
