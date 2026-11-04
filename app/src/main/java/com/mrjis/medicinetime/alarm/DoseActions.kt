package com.mrjis.medicinetime.alarm

import android.content.Context
import com.mrjis.medicinetime.data.local.dao.DoseLogDao
import com.mrjis.medicinetime.data.local.entity.DoseLogEntity
import com.mrjis.medicinetime.domain.model.DoseSource
import com.mrjis.medicinetime.domain.model.DoseStatus

/** One dose the user taps "Taken" on, from the Today screen. */
data class DoseRequest(
    val medicineId: String,
    val scheduledAtMillis: Long,
    val doseAmount: Double
)

/** What a dose log looked like before a change, so the change can be undone. null = it had no row. */
data class DoseUndo(
    val logId: String,
    val previous: DoseLogEntity?
)

/**
 * What happens when the user answers a dose, from the alarm screen, the notification
 * or the Today screen. Stock needs no change here: current stock is calculated from the TAKEN logs.
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

    /**
     * Taken from the Today screen. Works for any state of the dose: not rung yet (taken early),
     * ringing, snoozed, or already missed (taken late). Creates the log row when there is none.
     * Returns what is needed to undo it.
     */
    suspend fun takeScheduled(profileId: String, requests: List<DoseRequest>): List<DoseUndo> {
        val now = System.currentTimeMillis()
        val undos = requests.map { request ->
            val id = DoseLogEntity.idFor(request.medicineId, request.scheduledAtMillis)
            val previous = doseLogs.get(id)
            doseLogs.upsert(
                DoseLogEntity(
                    id = id,
                    profileId = profileId,
                    medicineId = request.medicineId,
                    source = DoseSource.SCHEDULED,
                    scheduledAt = request.scheduledAtMillis,
                    status = DoseStatus.TAKEN,
                    actionAt = now,
                    snoozeCount = previous?.snoozeCount ?: 0,
                    doseAmountSnapshot = previous?.doseAmountSnapshot ?: request.doseAmount,
                    updatedAt = now
                )
            )
            DoseUndo(id, previous)
        }
        afterManualChange(profileId)
        return undos
    }

    /**
     * "Take now" for an as-needed medicine. Writes one TAKEN row stamped with the current time.
     * Stock needs no change here: current stock is calculated from the TAKEN logs,
     * so this lowers it, and [undo] (which deletes the row) gives it back.
     * Never creates PENDING / SNOOZED / MISSED, so it can never trigger the missed warning.
     */
    suspend fun takeAsNeeded(profileId: String, medicineId: String, amount: Double): DoseUndo {
        val now = System.currentTimeMillis()
        val id = DoseLogEntity.idFor(medicineId, now)
        val previous = doseLogs.get(id)
        doseLogs.upsert(
            DoseLogEntity(
                id = id,
                profileId = profileId,
                medicineId = medicineId,
                source = DoseSource.AS_NEEDED,
                scheduledAt = now,
                status = DoseStatus.TAKEN,
                actionAt = now,
                snoozeCount = 0,
                doseAmountSnapshot = amount,
                updatedAt = now
            )
        )
        return DoseUndo(id, previous)
    }

    /** Reverts [takeScheduled]: restores the old row, or removes the row that did not exist before. */
    suspend fun undo(profileId: String, undos: List<DoseUndo>) {
        val now = System.currentTimeMillis()
        undos.forEach { undo ->
            val previous = undo.previous
            if (previous == null) {
                doseLogs.delete(undo.logId)
            } else {
                doseLogs.upsert(previous.copy(updatedAt = now))
            }
        }
        scheduler.scheduleNextSnooze(profileId)
    }

    private suspend fun finish(profileId: String) {
        scheduler.scheduleNextSnooze(profileId)
        AlarmService.dismiss(appContext, profileId)
    }

    /** After a Today-screen change: a snooze may be gone, and a ringing alarm may no longer be needed. */
    private suspend fun afterManualChange(profileId: String) {
        scheduler.scheduleNextSnooze(profileId)
        if (doseLogs.getPending(profileId).isEmpty()) {
            AlarmService.dismiss(appContext, profileId)
        }
    }
}