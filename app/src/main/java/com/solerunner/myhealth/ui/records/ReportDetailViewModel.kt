package com.solerunner.myhealth.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.lab.BiomarkerCatalog
import com.solerunner.myhealth.domain.model.Attachment
import com.solerunner.myhealth.domain.model.LabResult
import com.solerunner.myhealth.domain.model.Report
import com.solerunner.myhealth.domain.model.Sex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportDetailUiState(
    val loading: Boolean = true,
    val report: Report? = null,
    val results: List<LabResult> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
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
                val attachments = container.reports.attachments(reportId)
                ReportDetailUiState(loading = false, report = report, results = results, attachments = attachments)
            }.onSuccess { _state.value = it }
                .onFailure { _state.value = ReportDetailUiState(loading = false, error = it.message) }
        }
    }

    /** Saves an edited result, reclassifies it against the catalogue and marks it as user-edited. */
    fun updateResult(updated: LabResult) {
        val report = _state.value.report ?: return
        val biomarker = BiomarkerCatalog.byKey(updated.analyteKey)
        val canonical = updated.value?.let { value ->
            biomarker?.toCanonical(value, updated.unit) ?: updated.canonicalValue
        }
        val status = if (canonical != null && biomarker != null) {
            biomarker.classify(canonical, Sex.UNSPECIFIED)
        } else {
            updated.status
        }
        val edited = updated.copy(
            canonicalValue = canonical,
            canonicalUnit = biomarker?.canonicalUnit ?: updated.canonicalUnit,
            status = status,
            userEdited = true,
        )
        val results = _state.value.results.map { if (it.id == edited.id) edited else it }
        viewModelScope.launch {
            runCatching {
                container.reports.update(report, results)
                container.audit.log("report.result_edit", edited.displayName)
            }.onSuccess { _state.update { it.copy(results = results) } }
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
