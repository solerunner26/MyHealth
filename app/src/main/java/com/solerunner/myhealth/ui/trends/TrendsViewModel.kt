package com.solerunner.myhealth.ui.trends

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solerunner.myhealth.data.loadSnapshot
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.analytics.TrendAnalyzer
import com.solerunner.myhealth.domain.model.AnalyteCategory
import com.solerunner.myhealth.domain.model.AnalyteSeries
import com.solerunner.myhealth.domain.model.TrendSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendsUiState(
    val loading: Boolean = true,
    val all: List<AnalyteSeries> = emptyList(),
    val visible: List<AnalyteSeries> = emptyList(),
    val query: String = "",
    val category: AnalyteCategory? = null,
)

class TrendsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(TrendsUiState())
    val state: StateFlow<TrendsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.database.changes().collect { load() }
        }
    }

    private suspend fun load() {
        runCatching { container.loadSnapshot().series }
            .onSuccess { all ->
                _state.update { filter(it.copy(loading = false, all = all)) }
            }
            .onFailure { _state.update { it.copy(loading = false) } }
    }

    fun setQuery(query: String) = _state.update { filter(it.copy(query = query)) }

    fun setCategory(category: AnalyteCategory?) = _state.update { filter(it.copy(category = category)) }

    private fun filter(state: TrendsUiState): TrendsUiState {
        val query = state.query.trim()
        val visible = state.all.filter { series ->
            (state.category == null || series.category == state.category) &&
                (query.isEmpty() || series.displayName.contains(query, ignoreCase = true))
        }
        return state.copy(visible = visible)
    }
}

data class TrendDetailUiState(
    val loading: Boolean = true,
    val series: AnalyteSeries? = null,
    val summary: TrendSummary = TrendSummary(),
    val shareIntent: Intent? = null,
    val exported: Boolean = false,
    val error: String? = null,
)

class TrendDetailViewModel(
    private val container: AppContainer,
    private val key: String,
) : ViewModel() {

    private val _state = MutableStateFlow(TrendDetailUiState())
    val state: StateFlow<TrendDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        runCatching {
            val snapshot = container.loadSnapshot()
            val series = snapshot.series.firstOrNull { it.key == key }
                ?: TrendAnalyzer.series(key, emptyList(), snapshot.today)
            TrendDetailUiState(
                loading = false,
                series = series,
                summary = series?.let { TrendAnalyzer.summarise(it) } ?: TrendSummary(),
            )
        }.onSuccess { _state.value = it }
            .onFailure { _state.value = TrendDetailUiState(loading = false, error = it.message) }
    }

    fun exportCsv() {
        val series = _state.value.series ?: return
        viewModelScope.launch {
            runCatching { container.exporter.exportSeries(series) }
                .onSuccess { file ->
                    _state.update {
                        it.copy(shareIntent = container.exporter.shareIntent(file), exported = true)
                    }
                }
                .onFailure { error -> _state.update { it.copy(error = error.message) } }
        }
    }

    fun clearShare() = _state.update { it.copy(shareIntent = null) }
}
