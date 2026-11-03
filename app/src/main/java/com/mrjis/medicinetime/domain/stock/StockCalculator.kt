package com.mrjis.medicinetime.domain.stock

import com.mrjis.medicinetime.data.local.entity.DoseLogEntity
import com.mrjis.medicinetime.data.local.entity.MedicineEntity
import com.mrjis.medicinetime.data.local.entity.MedicineWithSchedule
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.domain.dose.DoseRules
import com.mrjis.medicinetime.domain.model.DoseStatus
import com.mrjis.medicinetime.domain.model.ScheduleMode
import com.mrjis.medicinetime.domain.model.StockUnit
import com.mrjis.medicinetime.domain.schedule.ScheduleResolver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Badge colour: OK = gray, LOW = amber, CRITICAL = red. */
enum class StockLevel { OK, LOW, CRITICAL }

/**
 * How long the stock lasts, for scheduled medicines.
 *  - [daysLeft] = days from today to the last day on which a dose is still fully covered.
 *    0 = the stock finishes today. -1 = not even the next planned dose is covered.
 *    null = the stock lasts until the end of the schedule (or nothing is planned).
 */
data class StockForecast(
    val daysLeft: Int?,
    val runOutDate: LocalDate?
)

data class StockInfo(
    val medicineId: String,
    /** false when no stock was entered (baseline 0). Nothing is warned or shown then. */
    val tracked: Boolean,
    /** In stock units (tablets, or ml for syrup). Never below 0. */
    val current: Double,
    val stockUnit: StockUnit,
    /** Scheduled medicines only. */
    val forecast: StockForecast?,
    val level: StockLevel
) {
    val daysLeft: Int? get() = forecast?.daysLeft
}

/** Pure logic (no Android classes). Stock = baseline minus the doses taken after the baseline. */
object StockCalculator {

    const val AMBER_DAYS = 7
    const val RED_DAYS = 2

    private const val EPS = 1e-9
    private const val MAX_FORECAST_DAYS = 400L

    /** A medicine with no stock entered (baseline 0) is not tracked. */
    fun isTracked(medicine: MedicineEntity): Boolean = medicine.stockBaseline > 0.0

    /** Stock used since the baseline, in stock units (2 tsp of syrup = 10 ml). */
    fun consumed(medicine: MedicineEntity, takenLogs: List<DoseLogEntity>): Double =
        takenLogs.asSequence()
            .filter {
                it.medicineId == medicine.id &&
                        it.status == DoseStatus.TAKEN &&
                        (it.actionAt ?: it.scheduledAt) > medicine.stockBaselineAt
            }
            .sumOf { medicine.doseUnit.toStockAmount(it.doseAmountSnapshot, medicine.stockUnit) }

    fun current(medicine: MedicineEntity, takenLogs: List<DoseLogEntity>): Double =
        (medicine.stockBaseline - consumed(medicine, takenLogs)).coerceAtLeast(0.0)

    /**
     * Walks forward day by day through the remaining phases, subtracting each planned dose,
     * until the stock cannot cover the next one. Handles weekly doses and tapering.
     *
     * [settledIds] are log ids already TAKEN or MISSED. They are not counted again
     * (taken ones are already out of [current]).
     */
    fun forecast(
        item: MedicineWithSchedule,
        profile: ProfileEntity,
        current: Double,
        nowMillis: Long,
        settledIds: Set<String>,
        zone: ZoneId = ZoneId.systemDefault()
    ): StockForecast {
        val medicine = item.medicine
        val lastEnd = item.phases.maxOfOrNull { it.phase.endDate } ?: return StockForecast(null, null)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val end = minOf(lastEnd, today.plusDays(MAX_FORECAST_DAYS))
        val single = listOf(item)

        var stock = current
        var lastCovered: LocalDate? = null
        var date = today
        while (!date.isAfter(end)) {
            for (dose in ScheduleResolver.occurrencesOn(single, profile, date)) {
                val at = dose.scheduledAtMillis(zone)
                if (at < medicine.updatedAt) continue // planned before the last edit
                if (DoseLogEntity.idFor(dose.medicineId, at) in settledIds) continue
                if (nowMillis - at > DoseRules.MISSED_WINDOW_MILLIS) continue // overdue, counts as missed

                val need = dose.doseUnit.toStockAmount(dose.doseAmount, dose.stockUnit)
                if (stock + EPS < need) {
                    val days = lastCovered?.let { ChronoUnit.DAYS.between(today, it).toInt() } ?: -1
                    return StockForecast(days, lastCovered)
                }
                stock -= need
                lastCovered = date
            }
            date = date.plusDays(1)
        }
        return StockForecast(null, null)
    }

    /**
     * @param takenLogs TAKEN logs since the medicine's baseline (see DoseLogDao.getTakenSince).
     * @param todayLogs logs around today (yesterday to tomorrow), used to skip settled doses.
     */
    fun info(
        item: MedicineWithSchedule,
        profile: ProfileEntity,
        takenLogs: List<DoseLogEntity>,
        todayLogs: List<DoseLogEntity>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): StockInfo {
        val medicine = item.medicine
        if (!isTracked(medicine)) {
            return StockInfo(medicine.id, false, 0.0, medicine.stockUnit, null, StockLevel.OK)
        }
        val current = current(medicine, takenLogs)

        if (medicine.scheduleMode == ScheduleMode.AS_NEEDED) {
            val low = medicine.lowStockLevel
            val level = when {
                current <= 0.0 -> StockLevel.CRITICAL
                low != null && low > 0.0 && current <= low + EPS -> StockLevel.LOW
                else -> StockLevel.OK
            }
            return StockInfo(medicine.id, true, current, medicine.stockUnit, null, level)
        }

        val settled = todayLogs
            .filter { it.status == DoseStatus.TAKEN || it.status == DoseStatus.MISSED }
            .map { it.id }
            .toSet()
        val forecast = forecast(item, profile, current, nowMillis, settled, zone)
        val days = forecast.daysLeft
        val level = when {
            days == null -> StockLevel.OK
            days <= RED_DAYS -> StockLevel.CRITICAL
            days <= AMBER_DAYS -> StockLevel.LOW
            else -> StockLevel.OK
        }
        return StockInfo(medicine.id, true, current, medicine.stockUnit, forecast, level)
    }
}