package com.solerunner.myhealth.ui.vitals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solerunner.myhealth.core.util.Dates
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.DataSource
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.domain.model.MeasurementKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class VitalsUiState(
    val loading: Boolean = true,
    val all: List<Measurement> = emptyList(),
    val selectedKind: MeasurementKind? = null,
    val message: String? = null,
) {
    val kinds: List<MeasurementKind> get() = all.map { it.kind }.distinct()
    val visible: List<Measurement> get() = all.filter { selectedKind == null || it.kind == selectedKind }
    val latest: Measurement? get() = visible.firstOrNull()
    val average7: Double? get() {
        val cutoff = Dates.today().minusDays(7)
        val values = visible.filter { !it.recordedDay.isBefore(cutoff) && it.kind != MeasurementKind.BLOOD_PRESSURE }
            .map { it.primaryValue }
        return values.takeIf { it.isNotEmpty() }?.average()
    }
}

class VitalsViewModel(private val container: AppContainer) : ViewModel() {

    private val selection = MutableStateFlow<MeasurementKind?>(null)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<VitalsUiState> = combine(
        container.measurements.observeAll(),
        selection,
        message,
    ) { all, selected, message ->
        VitalsUiState(
            loading = false,
            all = all.sortedWith(compareByDescending<Measurement> { it.recordedDay }.thenByDescending { it.recordedAt }),
            selectedKind = selected,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VitalsUiState())

    fun select(kind: MeasurementKind?) {
        selection.value = kind
    }

    fun add(kind: MeasurementKind, primary: Double, secondary: Double?, day: LocalDate, note: String, customLabel: String?) {
        viewModelScope.launch {
            val instant = day.atStartOfDay(ZoneId.systemDefault()).toInstant()
            container.measurements.add(
                Measurement(
                    kind = kind,
                    primaryValue = primary,
                    secondaryValue = secondary,
                    recordedAt = instant,
                    recordedDay = day,
                    source = DataSource.MANUAL,
                    note = note,
                    customLabel = customLabel?.takeIf { it.isNotBlank() },
                ),
            )
            container.audit.log("vital.add", kind.key)
            selection.value = kind
            message.value = "added"
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            container.measurements.delete(id)
            container.audit.log("vital.delete", "id=$id")
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun nowInstant(): Instant = Instant.now()
}
