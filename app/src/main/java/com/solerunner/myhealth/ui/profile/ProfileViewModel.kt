package com.solerunner.myhealth.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solerunner.myhealth.core.util.Dates
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Profile
import com.solerunner.myhealth.domain.model.Sex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class ProfileUiState(
    val loading: Boolean = true,
    val draft: Profile = Profile(displayName = ""),
    val saved: Boolean = false,
    val error: String? = null,
)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = container.profiles.current() ?: Profile(displayName = "")
            _state.value = ProfileUiState(loading = false, draft = profile)
        }
    }

    fun update(transform: (Profile) -> Profile) {
        _state.update { it.copy(draft = transform(it.draft), saved = false, error = null) }
    }

    fun setName(value: String) = update { it.copy(displayName = value) }
    fun setDateOfBirth(value: LocalDate?) = update { it.copy(dateOfBirth = value) }
    fun setSex(value: Sex) = update { it.copy(sex = value) }
    fun setHeight(value: Double?) = update { it.copy(heightCm = value) }
    fun setBloodGroup(value: String) = update { it.copy(bloodGroup = value.ifBlank { null }) }
    fun setAllergies(value: String) = update {
        it.copy(allergies = value.split(',').map { item -> item.trim() }.filter { item -> item.isNotEmpty() })
    }
    fun setConditions(value: String) = update {
        it.copy(conditions = value.split(',').map { item -> item.trim() }.filter { item -> item.isNotEmpty() })
    }
    fun setEmergencyName(value: String) = update { it.copy(emergencyContactName = value.ifBlank { null }) }
    fun setEmergencyPhone(value: String) = update { it.copy(emergencyContactPhone = value.ifBlank { null }) }
    fun setTargetWeight(value: Double?) = update { it.copy(targetWeightKg = value) }

    fun save() {
        val draft = _state.value.draft
        if (draft.displayName.trim().length < 2) {
            _state.update { it.copy(error = "name") }
            return
        }
        viewModelScope.launch {
            runCatching {
                container.profiles.save(draft.copy(updatedAt = Instant.now()))
            }.onSuccess {
                container.audit.log("profile.saved")
                _state.update { it.copy(saved = true) }
            }.onFailure { error -> _state.update { it.copy(error = error.message) } }
        }
    }

    fun clearSaved() = _state.update { it.copy(saved = false) }

    fun today(): LocalDate = Dates.today()
}
