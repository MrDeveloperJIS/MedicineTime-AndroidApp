package com.mrjis.medicinetime.domain.schedule

import com.mrjis.medicinetime.data.local.entity.MedicineWithSchedule
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.data.local.entity.SchedulePhaseEntity
import com.mrjis.medicinetime.data.local.entity.ScheduleSlotEntity
import com.mrjis.medicinetime.domain.model.DoseUnit
import com.mrjis.medicinetime.domain.model.RepeatType
import com.mrjis.medicinetime.domain.model.ScheduleMode
import com.mrjis.medicinetime.domain.model.SlotType
import com.mrjis.medicinetime.domain.model.StockUnit
import com.mrjis.medicinetime.domain.model.Weekdays
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One medicine due at one moment. */
data class DueDose(
    val medicineId: String,
    val medicineName: String,
    val doseAmount: Double,
    val doseUnit: DoseUnit,
    val stockUnit: StockUnit,
    val scheduledAt: LocalDateTime
) {
    fun scheduledAtMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        scheduledAt.atZone(zone).toInstant().toEpochMilli()
}

/** Everything of one profile that is due at the same minute (one alarm). */
data class DueGroup(
    val at: LocalDateTime,
    val doses: List<DueDose>
) {
    fun atMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        at.atZone(zone).toInstant().toEpochMilli()
}

/**
 * Pure schedule logic (no Android classes), so it is easy to unit test.
 * Pass the medicines of ONE profile and that profile (its meal times are needed).
 */
object ScheduleResolver {

    /** How far ahead we look for the next dose. Covers "every 30 days" with room to spare. */
    private const val SEARCH_DAYS = 400L

    /**
     * The next group of doses strictly after [after], or null if nothing is left
     * (all courses finished, or only as-needed medicines).
     */
    fun nextDue(
        medicines: List<MedicineWithSchedule>,
        profile: ProfileEntity,
        after: LocalDateTime
    ): DueGroup? {
        val start = after.toLocalDate().minusDays(1) // a meal offset can pull a dose into the previous day
        val candidates = mutableListOf<DueDose>()
        var firstFoundOn: LocalDate? = null
        var date = start
        val last = start.plusDays(SEARCH_DAYS)

        while (!date.isAfter(last)) {
            val found = occurrencesOn(medicines, profile, date).filter { it.scheduledAt.isAfter(after) }
            if (found.isNotEmpty()) {
                candidates += found
                if (firstFoundOn == null) firstFoundOn = date
            }
            // One extra day is scanned after the first hit, because a meal offset on the
            // next day could still land earlier than something found today.
            if (firstFoundOn != null && !date.isBefore(firstFoundOn.plusDays(1))) break
            date = date.plusDays(1)
        }

        val earliest = candidates.minOfOrNull { it.scheduledAt } ?: return null
        return DueGroup(earliest, candidates.filter { it.scheduledAt == earliest })
    }

    /** All doses due exactly at [at] (minute precision). Used when an alarm fires. */
    fun dueAt(
        medicines: List<MedicineWithSchedule>,
        profile: ProfileEntity,
        at: LocalDateTime
    ): List<DueDose> {
        val target = at.truncatedTo(ChronoUnit.MINUTES)
        val day = target.toLocalDate()
        return (-1L..1L).flatMap { offset ->
            occurrencesOn(medicines, profile, day.plusDays(offset))
        }.filter { it.scheduledAt == target }
    }

    /**
     * Every dose that belongs to [date], in time order. A dose "belongs" to the date of its
     * phase day, even if a meal offset moves its clock time into the previous or next day.
     */
    fun occurrencesOn(
        medicines: List<MedicineWithSchedule>,
        profile: ProfileEntity,
        date: LocalDate
    ): List<DueDose> {
        val result = mutableListOf<DueDose>()
        for (item in medicines) {
            val medicine = item.medicine
            if (medicine.scheduleMode != ScheduleMode.SCHEDULED || medicine.deletedAt != null) continue

            val phase = item.phases.firstOrNull { covers(it.phase, date) } ?: continue
            if (!repeatsOn(phase.phase, date)) continue

            for (slot in phase.slots) {
                val at = slotDateTime(slot, date, profile) ?: continue
                result += DueDose(
                    medicineId = medicine.id,
                    medicineName = medicine.name,
                    doseAmount = slot.doseAmount,
                    doseUnit = medicine.doseUnit,
                    stockUnit = medicine.stockUnit,
                    scheduledAt = at
                )
            }
        }
        return result.sortedBy { it.scheduledAt }
    }

    private fun covers(phase: SchedulePhaseEntity, date: LocalDate): Boolean =
        !date.isBefore(phase.startDate) && !date.isAfter(phase.endDate)

    private fun repeatsOn(phase: SchedulePhaseEntity, date: LocalDate): Boolean = when (phase.repeatType) {
        RepeatType.DAILY -> true
        RepeatType.WEEKDAYS -> Weekdays.contains(phase.repeatDays, date.dayOfWeek)
        RepeatType.EVERY_N_DAYS -> {
            val n = phase.intervalDays.coerceAtLeast(1)
            val days = ChronoUnit.DAYS.between(phase.anchorDate, date)
            days >= 0 && days % n == 0L
        }
    }

    private fun slotDateTime(
        slot: ScheduleSlotEntity,
        date: LocalDate,
        profile: ProfileEntity
    ): LocalDateTime? = when (slot.slotType) {
        SlotType.CLOCK -> slot.clockTime?.let { date.atTime(it) }
        SlotType.MEAL_BEFORE -> slot.meal?.let {
            date.atTime(profile.mealTime(it)).minusMinutes((slot.offsetMinutes ?: 0).toLong())
        }
        SlotType.MEAL_AFTER -> slot.meal?.let {
            date.atTime(profile.mealTime(it)).plusMinutes((slot.offsetMinutes ?: 0).toLong())
        }
    }?.truncatedTo(ChronoUnit.MINUTES)
}