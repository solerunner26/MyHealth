package com.solerunner.myhealth.ui.treatments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Treatment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TreatmentsUiState(
    val loading: Boolean = true,
    val treatments: List<Treatment> = emptyList(),
    val editing: Treatment? = null,
    val creating: Boolean = false,
)

class TreatmentsViewModel(private val container: AppContainer) : ViewModel() {

    private val editing = MutableStateFlow<Treatment?>(null)
    private val creating = MutableStateFlow(false)

    val state: StateFlow<TreatmentsUiState> = combine(
        container.treatments.observeAll(),
        editing,
        creating,
    ) { all, editingValue, creatingValue ->
        TreatmentsUiState(loading = false, treatments = all, editing = editingValue, creating = creatingValue)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TreatmentsUiState())

    fun create() { creating.value = true }

    fun edit(treatment: Treatment) { editing.value = treatment }

    fun dismiss() {
        creating.value = false
        editing.value = null
    }

    fun save(treatment: Treatment) {
        viewModelScope.launch {
            container.treatments.save(treatment)
            container.audit.log("treatment.save", treatment.name)
            dismiss()
        }
    }

    fun delete(treatment: Treatment) {
        viewModelScope.launch {
            container.treatments.delete(treatment.id)
            dismiss()
        }
    }
}
