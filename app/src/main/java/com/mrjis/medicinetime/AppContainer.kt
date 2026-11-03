package com.mrjis.medicinetime

import android.content.Context
import com.mrjis.medicinetime.alarm.AlarmScheduler
import com.mrjis.medicinetime.alarm.DoseActions
import com.mrjis.medicinetime.data.local.AppDatabase
import com.mrjis.medicinetime.data.prefs.AppPreferences
import com.mrjis.medicinetime.data.repository.CurrentProfileProvider
import com.mrjis.medicinetime.data.repository.MedicineRepository
import com.mrjis.medicinetime.data.repository.ProfileRepository
import com.mrjis.medicinetime.domain.dose.MissedDoseSweeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container. Created once in [MedicineTimeApplication]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.get(appContext)

    /** Lives as long as the process. Used for work that must not be cancelled with a screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences = AppPreferences(appContext)
    val profileRepository = ProfileRepository(database.profileDao())
    val medicineRepository = MedicineRepository(database.medicineDao())
    val currentProfileProvider = CurrentProfileProvider(profileRepository, preferences)
    val doseLogDao = database.doseLogDao()

    val alarmScheduler = AlarmScheduler(appContext, profileRepository, medicineRepository, doseLogDao)

    /** Taken / Snooze logic shared by the alarm screen, the notification buttons and the Today screen. */
    val doseActions = DoseActions(appContext, doseLogDao, alarmScheduler)

    /** Marks unanswered doses as MISSED. */
    val missedSweeper = MissedDoseSweeper(profileRepository, medicineRepository, doseLogDao)
}