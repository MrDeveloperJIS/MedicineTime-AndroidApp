package com.mrjis.medicinetime

import android.content.Context
import com.mrjis.medicinetime.data.local.AppDatabase
import com.mrjis.medicinetime.data.prefs.AppPreferences
import com.mrjis.medicinetime.data.repository.CurrentProfileProvider
import com.mrjis.medicinetime.data.repository.MedicineRepository
import com.mrjis.medicinetime.data.repository.ProfileRepository

/** Manual dependency container. Created once in [MedicineTimeApplication]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.get(appContext)

    val preferences = AppPreferences(appContext)
    val profileRepository = ProfileRepository(database.profileDao())
    val medicineRepository = MedicineRepository(database.medicineDao())
    val currentProfileProvider = CurrentProfileProvider(profileRepository, preferences)
    val doseLogDao = database.doseLogDao()
}