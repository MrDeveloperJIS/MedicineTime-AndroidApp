package com.mrjis.medicinetime.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mrjis.medicinetime.AppContainer
import com.mrjis.medicinetime.MedicineTimeApplication
import com.mrjis.medicinetime.ui.history.HistoryViewModel
import com.mrjis.medicinetime.ui.medicines.MedicineEditViewModel
import com.mrjis.medicinetime.ui.medicines.MedicinesViewModel
import com.mrjis.medicinetime.ui.profile.ProfilesViewModel
import com.mrjis.medicinetime.ui.settings.SettingsViewModel
import com.mrjis.medicinetime.ui.today.TodayViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { ProfilesViewModel(appContainer()) }
        initializer { MedicinesViewModel(appContainer()) }
        initializer { TodayViewModel(appContainer()) }
        initializer { HistoryViewModel(appContainer()) }
        initializer { SettingsViewModel(appContainer()) }
        // The SavedStateHandle carries the medicineId navigation argument.
        initializer { MedicineEditViewModel(createSavedStateHandle(), appContainer()) }
    }
}

private fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicineTimeApplication).container