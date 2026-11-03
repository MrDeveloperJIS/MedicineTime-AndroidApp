package com.mrjis.medicinetime.data.repository

import com.mrjis.medicinetime.data.local.dao.DoseLogDao
import com.mrjis.medicinetime.data.local.dao.MedicineDao
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.domain.dose.TodayPlanner
import com.mrjis.medicinetime.domain.stock.StockCalculator
import com.mrjis.medicinetime.domain.stock.StockInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StockRepository(
    private val medicines: MedicineRepository,
    private val medicineDao: MedicineDao,
    private val doseLogs: DoseLogDao
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Emits the current time now and every minute, so "days left" moves on at midnight. */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }

    /**
     * Live stock of every tracked medicine of [profile], by medicine id.
     * Medicines without stock (baseline 0) are not in the map.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeInfo(profile: ProfileEntity): Flow<Map<String, StockInfo>> =
        medicines.observeForProfile(profile.id).flatMapLatest { items ->
            val tracked = items.filter { StockCalculator.isTracked(it.medicine) }
            if (tracked.isEmpty()) {
                return@flatMapLatest flowOf<Map<String, StockInfo>>(emptyMap())
            }
            val since = tracked.minOf { it.medicine.stockBaselineAt }
            val taken = doseLogs.observeTakenSince(profile.id, since)
            val around = ticker
                .map { toDate(it) }
                .distinctUntilChanged()
                .flatMapLatest { date ->
                    val (from, to) = TodayPlanner.logRange(date, zone)
                    doseLogs.observeBetween(profile.id, from, to)
                }
            combine(taken, around, ticker) { takenList, aroundList, now ->
                tracked.associate { item ->
                    item.medicine.id to
                            StockCalculator.info(item, profile, takenList, aroundList, now, zone)
                }
            }
        }

    /** Current stock of one medicine in stock units, or null if it does not exist. */
    suspend fun currentStock(medicineId: String): Double? {
        val medicine = medicines.get(medicineId)?.medicine ?: return null
        val taken = doseLogs.getTakenSince(medicine.profileId, medicine.stockBaselineAt)
        return StockCalculator.current(medicine, taken)
    }

    /**
     * "I bought more": the new baseline is what is left now plus [amount] (in stock units),
     * starting from now. That also resets the stock warnings for this medicine.
     * Returns false if the amount is not above 0 or the medicine is gone.
     */
    suspend fun refill(medicineId: String, amount: Double): Boolean {
        if (amount <= 0.0) return false
        val medicine = medicines.get(medicineId)?.medicine ?: return false
        val now = System.currentTimeMillis()
        val taken = doseLogs.getTakenSince(medicine.profileId, medicine.stockBaselineAt)
        val current = StockCalculator.current(medicine, taken)
        medicineDao.setStock(medicineId, current + amount, now)
        return true
    }

    private fun toDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private companion object {
        const val TICK_MILLIS = 60_000L
    }
}