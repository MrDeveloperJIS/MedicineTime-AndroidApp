package com.mrjis.medicinetime.domain.stock

import com.mrjis.medicinetime.data.local.dao.DoseLogDao
import com.mrjis.medicinetime.data.prefs.StockWarningStore
import com.mrjis.medicinetime.data.repository.MedicineRepository
import com.mrjis.medicinetime.data.repository.ProfileRepository
import com.mrjis.medicinetime.domain.dose.TodayPlanner
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * Decides which stock warnings to send. Runs daily from WorkManager (added in 4C).
 * It only decides: showing the notification is done by the [onWarning] callback.
 * A warning is sent when it is more serious than the last one sent for this refill.
 */
class StockChecker(
    private val profiles: ProfileRepository,
    private val medicines: MedicineRepository,
    private val doseLogs: DoseLogDao,
    private val store: StockWarningStore
) {

    /** Returns how many warnings were sent. */
    suspend fun check(
        nowMillis: Long = System.currentTimeMillis(),
        onWarning: suspend (StockWarning) -> Unit
    ): Int {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        var sent = 0

        for (profile in profiles.observeAll().first()) {
            val items = medicines.observeForProfile(profile.id).first()
            val tracked = items.filter { StockCalculator.isTracked(it.medicine) }
            if (tracked.isEmpty()) continue

            val since = tracked.minOf { it.medicine.stockBaselineAt }
            val taken = doseLogs.getTakenSince(profile.id, since)
            val (from, to) = TodayPlanner.logRange(today, zone)
            val todayLogs = doseLogs.getBetween(profile.id, from, to)

            for (item in tracked) {
                val medicine = item.medicine
                val info = StockCalculator.info(item, profile, taken, todayLogs, nowMillis, zone)
                val warning = StockWarningPolicy.evaluate(profile, medicine, info)

                val stored = store.get(medicine.id)
                // State from before the last refill or stock edit does not count.
                val last = stored?.takeIf { it.baselineAt == medicine.stockBaselineAt }

                when {
                    // Stock is fine again: forget, so the next drop is warned about again.
                    warning == null -> if (stored != null) store.clear(medicine.id)

                    last == null || warning.rank < last.rank -> {
                        onWarning(warning) // if this throws, nothing is stored and tomorrow retries
                        store.set(medicine.id, medicine.stockBaselineAt, warning.rank)
                        sent++
                    }

                    // Less serious than before (for example the course was extended): no message.
                    warning.rank > last.rank ->
                        store.set(medicine.id, medicine.stockBaselineAt, warning.rank)
                }
            }
        }
        return sent
    }
}