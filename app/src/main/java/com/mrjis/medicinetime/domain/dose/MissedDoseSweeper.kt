package com.mrjis.medicinetime.domain.dose

import com.mrjis.medicinetime.data.local.dao.DoseLogDao
import com.mrjis.medicinetime.data.local.entity.DoseLogEntity
import com.mrjis.medicinetime.data.repository.MedicineRepository
import com.mrjis.medicinetime.data.repository.ProfileRepository
import com.mrjis.medicinetime.domain.model.DoseSource
import com.mrjis.medicinetime.domain.model.DoseStatus
import com.mrjis.medicinetime.domain.schedule.ScheduleResolver
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * Marks doses MISSED. Runs on app start now, and daily from WorkManager in M3C.
 *  1. Doses that rang and were never answered (PENDING / SNOOZED past the missed window).
 *  2. Planned doses that never got a log row at all (phone was off, alarm lost).
 * As-needed doses are never touched.
 */
class MissedDoseSweeper(
    private val profiles: ProfileRepository,
    private val medicines: MedicineRepository,
    private val doseLogs: DoseLogDao
) {

    /** Returns how many doses were marked MISSED. */
    suspend fun sweep(nowMillis: Long = System.currentTimeMillis()): Int {
        var changed = 0

        doseLogs.getOpen().forEach { log ->
            if (DoseRules.isMissed(log, nowMillis)) {
                doseLogs.upsert(log.copy(status = DoseStatus.MISSED, updatedAt = nowMillis))
                changed++
            }
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val oldest = nowMillis - DoseRules.SWEEP_LOOKBACK_DAYS * 24 * 60 * 60_000L

        profiles.observeAll().first().forEach { profile ->
            val items = medicines.observeForProfile(profile.id).first()
            val updatedAt = items.associate { it.medicine.id to it.medicine.updatedAt }

            for (back in 0L..DoseRules.SWEEP_LOOKBACK_DAYS) {
                val date = today.minusDays(back)
                for (dose in ScheduleResolver.occurrencesOn(items, profile, date)) {
                    val at = dose.scheduledAtMillis(zone)
                    if (nowMillis - at <= DoseRules.MISSED_WINDOW_MILLIS) continue // not overdue yet
                    if (at < oldest) continue
                    if (at < (updatedAt[dose.medicineId] ?: 0L)) continue // planned before the last edit

                    val id = DoseLogEntity.idFor(dose.medicineId, at)
                    if (doseLogs.get(id) != null) continue

                    doseLogs.upsert(
                        DoseLogEntity(
                            id = id,
                            profileId = profile.id,
                            medicineId = dose.medicineId,
                            source = DoseSource.SCHEDULED,
                            scheduledAt = at,
                            status = DoseStatus.MISSED,
                            doseAmountSnapshot = dose.doseAmount,
                            updatedAt = nowMillis
                        )
                    )
                    changed++
                }
            }
        }
        return changed
    }
}