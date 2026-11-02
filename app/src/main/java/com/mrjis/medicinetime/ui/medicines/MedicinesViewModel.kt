package com.mrjis.medicinetime.ui.medicines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrjis.medicinetime.AppContainer
import com.mrjis.medicinetime.data.local.entity.MedicineWithSchedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class MedicinesViewModel(container: AppContainer) : ViewModel() {

    /** Medicines of the current profile. null until the first load finishes. */
    val medicines: StateFlow<List<MedicineWithSchedule>?> = container.currentProfileProvider.current
        .flatMapLatest { profile ->
            if (profile == null) flowOf(emptyList())
            else container.medicineRepository.observeForProfile(profile.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}