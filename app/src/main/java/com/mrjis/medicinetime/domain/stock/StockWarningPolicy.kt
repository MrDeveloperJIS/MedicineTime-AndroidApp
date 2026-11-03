package com.mrjis.medicinetime.domain.stock

import com.mrjis.medicinetime.data.local.entity.MedicineEntity
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.domain.model.ScheduleMode

enum class StockWarningKind {
    /** Scheduled: 1 to 7 days of stock left. */
    DAYS_LEFT,

    /** Scheduled: the last covered dose is today. */
    FINISHES_TODAY,

    /** Scheduled: the next planned dose is not covered any more. */
    OUT,

    /** As needed: stock is at or below the level the user set. */
    AS_NEEDED_LOW,

    /** As needed: stock is 0. */
    AS_NEEDED_OUT
}

/**
 * [rank] says how serious the warning is. A smaller rank is more serious, and a warning is
 * only sent when the rank is smaller than the one already sent for this refill.
 */
data class StockWarning(
    val profile: ProfileEntity,
    val medicine: MedicineEntity,
    val kind: StockWarningKind,
    val rank: Int,
    /** Actual days left (DAYS_LEFT only). */
    val daysLeft: Int?,
    val current: Double
)

object StockWarningPolicy {

    /** Plan 6: warn at 7, 5, 3, 2, 1 days left, and on the day it runs out (0). */
    private val DAY_THRESHOLDS = listOf(1, 2, 3, 5, 7)

    private const val RANK_AS_NEEDED_LOW = 100
    private const val RANK_OUT = -1

    /** The warning that applies right now, or null when the stock is fine. */
    fun evaluate(profile: ProfileEntity, medicine: MedicineEntity, info: StockInfo): StockWarning? {
        if (!info.tracked) return null

        if (medicine.scheduleMode == ScheduleMode.AS_NEEDED) {
            val low = medicine.lowStockLevel
            return when {
                info.current <= 0.0 ->
                    StockWarning(profile, medicine, StockWarningKind.AS_NEEDED_OUT, RANK_OUT, null, info.current)

                low != null && low > 0.0 && info.current <= low ->
                    StockWarning(profile, medicine, StockWarningKind.AS_NEEDED_LOW, RANK_AS_NEEDED_LOW, null, info.current)

                else -> null
            }
        }

        val days = info.daysLeft ?: return null // lasts until the end of the schedule
        return when {
            days < 0 -> StockWarning(profile, medicine, StockWarningKind.OUT, RANK_OUT, null, info.current)
            days == 0 -> StockWarning(profile, medicine, StockWarningKind.FINISHES_TODAY, 0, 0, info.current)
            days <= DAY_THRESHOLDS.last() -> {
                val rank = DAY_THRESHOLDS.first { it >= days }
                StockWarning(profile, medicine, StockWarningKind.DAYS_LEFT, rank, days, info.current)
            }
            else -> null
        }
    }
}