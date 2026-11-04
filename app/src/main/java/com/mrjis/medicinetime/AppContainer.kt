package com.mrjis.medicinetime

import android.content.Context
import com.mrjis.medicinetime.alarm.AlarmScheduler
import com.mrjis.medicinetime.alarm.DoseActions
import com.mrjis.medicinetime.data.local.AppDatabase
import com.mrjis.medicinetime.data.prefs.AppPreferences
import com.mrjis.medicinetime.data.prefs.SettingsStore
import com.mrjis.medicinetime.data.prefs.StockWarningStore
import com.mrjis.medicinetime.data.repository.CurrentProfileProvider
import com.mrjis.medicinetime.data.repository.HistoryRepository
import com.mrjis.medicinetime.data.repository.MedicineRepository
import com.mrjis.medicinetime.data.repository.ProfileRepository
import com.mrjis.medicinetime.data.repository.StockRepository
import com.mrjis.medicinetime.domain.dose.MissedDoseSweeper
import com.mrjis.medicinetime.domain.stock.StockChecker
import com.mrjis.medicinetime.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container. Created once in [MedicineTimeApplication]. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val database = AppDatabase.get(appContext)

    /** Lives as long as the process. Used for work that must not be cancelled with a screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Theme, language, snooze options, missed window and ringtone.
     * Created first: it also hands the missed window to DoseRules.
     */
    val settingsStore = SettingsStore(appContext)

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

    /** Current stock, days left and "I bought more". */
    val stockRepository = StockRepository(medicineRepository, database.medicineDao(), doseLogDao)

    /** Remembers which stock warnings were already sent (per refill). */
    val stockWarningStore = StockWarningStore(appContext)

    /** Decides which low-stock warnings to send. Run daily by StockCheckWorker. */
    val stockChecker = StockChecker(profileRepository, medicineRepository, doseLogDao, stockWarningStore)

    /** History days (timeline, calendar, adherence) and deleting a logged as-needed dose. */
    val historyRepository = HistoryRepository(medicineRepository, doseLogDao)

    /** Looks for a newer release on GitHub. Only runs when the user taps the button in Settings. */
    val updateChecker = UpdateChecker(appContext)
}