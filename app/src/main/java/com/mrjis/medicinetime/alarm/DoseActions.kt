package com.mrjis.medicinetime.alarm

import android.content.Context
import com.mrjis.medicinetime.data.local.dao.DoseLogDao
import com.mrjis.medicinetime.domain.model.DoseStatus

/** Alarm defaults. Becomes user settings in M6. */
object AlarmDefaults {
    val SNOOZE_OPTIONS_MINUTES = listOf(15, 30, 60)
    const val DEFAULT_SNOOZE_MINUTES = 15
}

/**
 * What happens when the user answers an alarm, from the alarm screen or from the notification.
 * Stock needs no change here: current stock is calculated from the TAKEN logs.
 */
class DoseActions(
    context: Context,
    private val doseLogs: DoseLogDao,
    private val scheduler: AlarmScheduler
) {
    private val appContext = context.applicationContext

    /** Marks the doses TAKEN and stops the ringing for this profile. */
    suspend fun taken(profileId: String, logIds: Collection<String>) {
        val now = System.currentTimeMillis()
        logIds.forEach { id ->
            val log = doseLogs.get(id) ?: return@forEach
            if (log.status == DoseStatus.TAKEN) return@forEach
            doseLogs.upsert(log.copy(status = DoseStatus.TAKEN, actionAt = now, updatedAt = now))
        }
        finish(profileId)
    }

    /**
     * Snoozes the doses that are still waiting, then stops the ringing.
     * For a SNOOZED row, actionAt holds the time it must ring again.
     */
    suspend fun snooze(profileId: String, logIds: Collection<String>, minutes: Int) {
        val now = System.currentTimeMillis()
        val ringAgainAt = now + minutes * 60_000L
        logIds.forEach { id ->
            val log = doseLogs.get(id) ?: return@forEach
            if (log.status != DoseStatus.PENDING) return@forEach
            doseLogs.upsert(
                log.copy(
                    status = DoseStatus.SNOOZED,
                    snoozeCount = log.snoozeCount + 1,
                    actionAt = ringAgainAt,
                    updatedAt = now
                )
            )
        }
        finish(profileId)
    }

    private suspend fun finish(profileId: String) {
        scheduler.scheduleNextSnooze(profileId)
        AlarmService.dismiss(appContext, profileId)
    }
}