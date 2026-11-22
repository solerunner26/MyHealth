package com.solerunner.myhealth.ui.medicines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Medicine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MedicineTab { ACTIVE, PAST }

data class MedicinesUiState(
    val loading: Boolean = true,
    val active: List<Medicine> = emptyList(),
    val past: List<Medicine> = emptyList(),
    val tab: MedicineTab = MedicineTab.ACTIVE,
    val editing: Medicine? = null,
    val creating: Boolean = false,
    val message: String? = null,
) {
    val visible: List<Medicine> get() = if (tab == MedicineTab.ACTIVE) active else past
}

class MedicinesViewModel(private val container: AppContainer) : ViewModel() {

    private val tab = MutableStateFlow(MedicineTab.ACTIVE)
    private val editing = MutableStateFlow<Medicine?>(null)
    private val creating = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<MedicinesUiState> = combine(
        container.medicines.observeAll(),
        combine(tab, editing, creating) { tabValue, editingValue, creatingValue -> Triple(tabValue, editingValue, creatingValue) },
        message,
    ) { all, form, messageValue ->
        MedicinesUiState(
            loading = false,
            active = all.filter { it.isActive }.sortedBy { it.name.lowercase() },
            past = all.filterNot { it.isActive }.sortedByDescending { it.endDate },
            tab = form.first,
            editing = form.second,
            creating = form.third,
            message = messageValue,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicinesUiState())

    fun selectTab(value: MedicineTab) {
        tab.value = value
    }

    fun create() {
        creating.value = true
    }

    fun edit(medicine: Medicine) {
        editing.value = medicine
    }

    fun dismissForm() {
        creating.value = false
        editing.value = null
    }

    fun save(medicine: Medicine) {
        viewModelScope.launch {
            runCatching { container.medicines.save(medicine) }
                .onSuccess {
                    container.audit.log("medicine.save", medicine.name)
                    message.value = "saved"
                    dismissForm()
                }
                .onFailure { message.value = it.message }
        }
    }

    fun delete(medicine: Medicine) {
        viewModelScope.launch {
            container.medicines.delete(medicine.id)
            container.audit.log("medicine.delete", medicine.name)
            dismissForm()
        }
    }

    fun consumeMessage() {
        message.value = null
    }
}
