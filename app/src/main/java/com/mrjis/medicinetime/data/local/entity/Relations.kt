package com.mrjis.medicinetime.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class PhaseWithSlots(
    @Embedded val phase: SchedulePhaseEntity,
    @Relation(parentColumn = "id", entityColumn = "phaseId")
    val slots: List<ScheduleSlotEntity>
)

data class MedicineWithSchedule(
    @Embedded val medicine: MedicineEntity,
    @Relation(
        entity = SchedulePhaseEntity::class,
        parentColumn = "id",
        entityColumn = "medicineId"
    )
    val phases: List<PhaseWithSlots>
)