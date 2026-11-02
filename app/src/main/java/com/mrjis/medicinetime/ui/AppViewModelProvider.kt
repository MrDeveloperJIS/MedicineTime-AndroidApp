package com.mrjis.medicinetime.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mrjis.medicinetime.AppContainer
import com.mrjis.medicinetime.MedicineTimeApplication
import com.mrjis.medicinetime.ui.medicines.MedicinesViewModel
import com.mrjis.medicinetime.ui.profile.ProfilesViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { ProfilesViewModel(appContainer()) }
        initializer { MedicinesViewModel(appContainer()) }
    }
}

private fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicineTimeApplication).container