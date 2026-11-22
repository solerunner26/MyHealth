package com.solerunner.myhealth.ui.importdoc

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solerunner.myhealth.data.import.ImportDraft
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.lab.ParsedAnalyteRow
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.domain.model.Report
import com.solerunner.myhealth.ui.common.formatNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Reading(val message: String? = null) : ImportUiState
    data class Review(
        val draft: ImportDraft,
        val report: Report,
        val rows: List<ParsedAnalyteRow>,
        val medicines: List<Medicine>,
        val selectedMedicines: Set<Int> = medicines.indices.toSet(),
        val saving: Boolean = false,
        val error: String? = null,
    ) : ImportUiState
    data class Saved(val reportId: Long) : ImportUiState
    data class Failed(val message: String) : ImportUiState
}

/**
 * Drives one import. Reading happens off the main thread and nothing is written until [save]; the review
 * state is the only place the user can correct OCR mistakes, which is why every row is editable.
 */
class ImportViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun read(uri: Uri, displayName: String?, mimeType: String?) {
        viewModelScope.launch {
            _state.value = ImportUiState.Reading()
            runCatching { container.importer.read(uri, displayName, mimeType) }
                .onSuccess { draft ->
                    _state.value = ImportUiState.Review(
                        draft = draft,
                        report = container.importer.defaultReport(draft),
                        rows = draft.document.analytes,
                        medicines = draft.medicines.map { parsed ->
                            Medicine(
                                name = parsed.name,
                                molecule = parsed.molecule,
                                form = parsed.form,
                                strength = parsed.strength,
                                frequencyText = parsed.frequencyText,
                                timesPerDay = parsed.timesPerDay,
                                durationDays = parsed.durationDays,
                            )
                        },
                    )
                }
                .onFailure { error -> _state.value = ImportUiState.Failed(error.message ?: "read") }
        }
    }

    fun updateReport(transform: (Report) -> Report) {
        val current = _state.value as? ImportUiState.Review ?: return
        _state.value = current.copy(report = transform(current.report), error = null)
    }

    fun editValue(index: Int, raw: String) {
        val current = _state.value as? ImportUiState.Review ?: return
        val row = current.rows.getOrNull(index) ?: return
        val parsed = raw.trim().replace(',', '.').toDoubleOrNull()
        val updated = current.rows.toMutableList()
        updated[index] = row.copy(
            printedValue = parsed,
            status = com.solerunner.myhealth.domain.model.ValueStatus.UNKNOWN,
        )
        _state.value = current.copy(rows = updated)
    }

    fun removeRow(index: Int) {
        val current = _state.value as? ImportUiState.Review ?: return
        _state.value = current.copy(rows = current.rows.filterIndexed { i, _ -> i != index })
    }

    fun toggleMedicine(index: Int) {
        val current = _state.value as? ImportUiState.Review ?: return
        val selected = current.selectedMedicines.toMutableSet()
        if (!selected.add(index)) selected.remove(index)
        _state.value = current.copy(selectedMedicines = selected)
    }

    fun save() {
        val current = _state.value as? ImportUiState.Review ?: return
        if (current.saving) return
        _state.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            val chosen = current.selectedMedicines.mapNotNull { current.medicines.getOrNull(it) }
            runCatching {
                container.importer.save(current.draft, current.report, current.rows, chosen)
            }.onSuccess { id ->
                _state.value = ImportUiState.Saved(id)
            }.onFailure { error ->
                _state.value = current.copy(saving = false, error = error.message)
            }
        }
    }

    fun reset() {
        _state.value = ImportUiState.Idle
    }

    /** Used by the UI to prefill a value field with the parsed reading. */
    fun rowValue(row: ParsedAnalyteRow): String = row.printedValue?.let { formatNumber(it, row.decimals) } ?: ""
}

private fun MutableStateFlow<ImportUiState>.value(): ImportUiState = value
