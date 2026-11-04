package com.mrjis.medicinetime.ui.medicines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrjis.medicinetime.AppContainer
import com.mrjis.medicinetime.data.local.entity.MedicineWithSchedule
import com.mrjis.medicinetime.domain.stock.StockInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MedicinesViewModel(private val container: AppContainer) : ViewModel() {

    /** Medicines of the current profile. null until the first load finishes. */
    val medicines: StateFlow<List<MedicineWithSchedule>?> = container.currentProfileProvider.current
        .flatMapLatest { profile ->
            if (profile == null) flowOf(emptyList())
            else container.medicineRepository.observeForProfile(profile.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Live stock of the current profile's medicines, by medicine id. Untracked medicines are not in it. */
    val stock: StateFlow<Map<String, StockInfo>> = container.currentProfileProvider.current
        .flatMapLatest { profile ->
            if (profile == null) flowOf<Map<String, StockInfo>>(emptyMap())
            else container.stockRepository.observeInfo(profile)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** "I bought more". [amount] is in stock units. Runs in appScope so it finishes even if the screen closes. */
    fun refill(medicineId: String, amount: Double) {
        container.appScope.launch {
            container.stockRepository.refill(medicineId, amount)
        }
    }
}