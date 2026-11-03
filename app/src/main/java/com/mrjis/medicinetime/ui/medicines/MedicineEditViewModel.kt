package com.mrjis.medicinetime.ui.medicines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrjis.medicinetime.AppContainer
import com.mrjis.medicinetime.data.local.entity.MedicineEntity
import com.mrjis.medicinetime.domain.model.MedicineForm
import com.mrjis.medicinetime.domain.model.ScheduleMode
import com.mrjis.medicinetime.ui.navigation.Routes
import com.mrjis.medicinetime.util.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Add (no id) or edit (id from the navigation argument) a medicine. */
class MedicineEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val container: AppContainer
) : ViewModel() {

    private val medicineId: String? = savedStateHandle[Routes.MEDICINE_ID_ARG]

    val isEditing: Boolean = medicineId != null

    private var existing: MedicineEntity? = null

    private val _draft = MutableStateFlow(MedicineDraft())
    val draft: StateFlow<MedicineDraft> = _draft.asStateFlow()

    /** false while an existing medicine is still loading. */
    private val _loaded = MutableStateFlow(medicineId == null)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Turns true after the first failed Save, so errors don't show while the user is still typing. */
    private val _showErrors = MutableStateFlow(false)
    val showErrors: StateFlow<Boolean> = _showErrors.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** Becomes true once saved, deleted, or if the medicine no longer exists. The screen then closes. */
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    val validation: StateFlow<DraftValidation> = _draft
        .map { it.validate() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _draft.value.validate())

    init {
        if (medicineId != null) {
            viewModelScope.launch {
                val item = container.medicineRepository.get(medicineId)
                if (item == null) {
                    _finished.value = true
                } else {
                    existing = item.medicine
                    _draft.value = item.toDraft()
                    _loaded.value = true
                }
            }
        }
    }

    fun update(transform: (MedicineDraft) -> MedicineDraft) {
        _draft.update(transform)
    }

    /** Changing the form also fixes the dose unit if the old one doesn't fit (e.g. tablet -> syrup). */
    fun setForm(form: MedicineForm) {
        _draft.update { d ->
            d.copy(
                form = form,
                doseUnit = if (d.doseUnit in form.allowedDoseUnits()) d.doseUnit else form.defaultDoseUnit()
            )
        }
    }

    fun setScheduleMode(mode: ScheduleMode) {
        _draft.update { it.copy(scheduleMode = mode) }
    }

    fun save() {
        val d = _draft.value
        if (!d.validate().isValid) {
            _showErrors.value = true
            return
        }
        if (_saving.value) return
        _saving.value = true
        // appScope (not viewModelScope): the save and the alarm re-plan must finish
        // even if the screen is closed right after pressing Save.
        container.appScope.launch {
            try {
                val profileId = existing?.profileId
                    ?: container.currentProfileProvider.current.filterNotNull().first().id
                val id = medicineId ?: newId()
                val (entity, phases) = d.toEntities(id, profileId, existing, System.currentTimeMillis())
                container.medicineRepository.save(entity, phases)
                // The schedule changed, so re-plan this profile's next alarm.
                container.alarmScheduler.scheduleNextById(profileId)
                _finished.value = true
            } finally {
                _saving.value = false
            }
        }
    }

    fun delete() {
        val id = medicineId ?: return
        container.appScope.launch {
            container.medicineRepository.delete(id)
            existing?.profileId?.let { container.alarmScheduler.scheduleNextById(it) }
            _finished.value = true
        }
    }
}