package com.mrjis.medicinetime.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mrjis.medicinetime.domain.model.RepeatType
import com.mrjis.medicinetime.domain.model.Weekdays
import java.time.LocalDate

@Entity(
    tableName = "schedule_phases",
    foreignKeys = [
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("medicineId")]
)
data class SchedulePhaseEntity(
    @PrimaryKey val id: String,
    val medicineId: String,
    val phaseOrder: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val repeatType: RepeatType = RepeatType.DAILY,
    /** Weekday bitmask, used when [repeatType] is WEEKDAYS. See [Weekdays]. */
    val repeatDays: Int = Weekdays.ALL,
    /** Used when [repeatType] is EVERY_N_DAYS. */
    val intervalDays: Int = 1,
    /** "Day 1" for every-N-days math. */
    val anchorDate: LocalDate
)