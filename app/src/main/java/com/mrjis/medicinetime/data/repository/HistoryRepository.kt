package com.mrjis.medicinetime.data.repository

import com.mrjis.medicinetime.data.local.dao.DoseLogDao
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.domain.history.HistoryDay
import com.mrjis.medicinetime.domain.history.HistoryPlanner
import com.mrjis.medicinetime.domain.model.DoseSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate
import java.time.ZoneId

class HistoryRepository(
    private val medicines: MedicineRepository,
    private val doseLogs: DoseLogDao
) {

    /** Emits the current time now and every minute, so open doses turn into missed ones live. */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }

    /**
     * The days from [from] to [to] (both included) of [profile], newest first.
     * Updates live when a dose is logged, a medicine is edited, or time passes.
     */
    fun observeDays(
        profile: ProfileEntity,
        from: LocalDate,
        to: LocalDate
    ): Flow<List<HistoryDay>> {
        val zone = ZoneId.systemDefault()
        val (fromMillis, toMillis) = HistoryPlanner.logRange(from, to, zone)
        return combine(
            medicines.observeForProfile(profile.id),
            doseLogs.observeHistory(profile.id, fromMillis, toMillis),
            ticker
        ) { items, rows, now ->
            HistoryPlanner.buildDays(items, profile, from, to, rows, now, zone)
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    /**
     * Deletes a logged as-needed dose. Stock is added back without any extra write, because
     * current stock is calculated from the TAKEN logs. Scheduled doses cannot be deleted here.
     * Returns false when the entry does not exist or is not an as-needed dose.
     */
    suspend fun deleteAsNeeded(logId: String): Boolean {
        val log = doseLogs.get(logId) ?: return false
        if (log.source != DoseSource.AS_NEEDED) return false
        doseLogs.delete(logId)
        return true
    }

    private companion object {
        const val TICK_MILLIS = 60_000L
    }
}