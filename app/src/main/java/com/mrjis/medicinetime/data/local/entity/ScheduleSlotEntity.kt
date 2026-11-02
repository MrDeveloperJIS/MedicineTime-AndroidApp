package com.mrjis.medicinetime.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mrjis.medicinetime.domain.model.Meal
import com.mrjis.medicinetime.domain.model.SlotType
import java.time.LocalTime

@Entity(
    tableName = "schedule_slots",
    foreignKeys = [
        ForeignKey(
            entity = SchedulePhaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["phaseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("phaseId")]
)
data class ScheduleSlotEntity(
    @PrimaryKey val id: String,
    val phaseId: String,
    val slotType: SlotType = SlotType.CLOCK,
    /** CLOCK slots only. */
    val clockTime: LocalTime? = null,
    /** MEAL_BEFORE / MEAL_AFTER slots only. */
    val meal: Meal? = null,
    /** Minutes before/after the meal. */
    val offsetMinutes: Int? = null,
    val doseAmount: Double = 1.0
)