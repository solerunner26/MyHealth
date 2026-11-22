package com.solerunner.myhealth.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Report
import com.solerunner.myhealth.domain.model.ReportKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class RecordsFilter(
    val query: String = "",
    val year: Int? = null,
    val kind: ReportKind? = null,
)

data class RecordsUiState(
    val loading: Boolean = true,
    val all: List<Report> = emptyList(),
    val visible: List<Report> = emptyList(),
    val filter: RecordsFilter = RecordsFilter(),
) {
    val years: List<Int> get() = all.map { it.sampleDate.year }.distinct().sortedDescending()
}

class RecordsViewModel(private val container: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(RecordsFilter())

    val state: StateFlow<RecordsUiState> = combine(container.reports.observeAll(), filter) { reports, active ->
        val visible = reports.filter { report ->
            val matchesYear = active.year == null || report.sampleDate.year == active.year
            val matchesKind = active.kind == null || report.kind == active.kind
            val query = active.query.trim()
            val matchesQuery = query.isEmpty() ||
                report.title.contains(query, ignoreCase = true) ||
                (report.labName?.contains(query, ignoreCase = true) == true) ||
                (report.doctorName?.contains(query, ignoreCase = true) == true)
            matchesYear && matchesKind && matchesQuery
        }
        RecordsUiState(loading = false, all = reports, visible = visible, filter = active)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsUiState())

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    fun setYear(year: Int?) = filter.update { it.copy(year = year) }

    fun setKind(kind: ReportKind?) = filter.update { it.copy(kind = kind) }
}
