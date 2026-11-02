package com.mrjis.medicinetime.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mrjis.medicinetime.data.local.entity.MedicineEntity
import com.mrjis.medicinetime.data.local.entity.MedicineWithSchedule
import com.mrjis.medicinetime.data.local.entity.PhaseWithSlots
import com.mrjis.medicinetime.data.local.entity.SchedulePhaseEntity
import com.mrjis.medicinetime.data.local.entity.ScheduleSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MedicineDao {

    @Transaction
    @Query(
        "SELECT * FROM medicines WHERE profileId = :profileId AND deletedAt IS NULL " +
                "ORDER BY name COLLATE NOCASE"
    )
    abstract fun observeForProfile(profileId: String): Flow<List<MedicineWithSchedule>>

    @Transaction
    @Query("SELECT * FROM medicines WHERE id = :id AND deletedAt IS NULL")
    abstract fun observe(id: String): Flow<MedicineWithSchedule?>

    @Transaction
    @Query("SELECT * FROM medicines WHERE id = :id AND deletedAt IS NULL")
    abstract suspend fun get(id: String): MedicineWithSchedule?

    @Query("UPDATE medicines SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    abstract suspend fun softDelete(id: String, now: Long)

    // Upsert (insert or update) never deletes the old row, so child rows are not cascaded away.
    @Upsert
    protected abstract suspend fun upsertMedicine(medicine: MedicineEntity)

    @Upsert
    protected abstract suspend fun upsertPhases(phases: List<SchedulePhaseEntity>)

    @Upsert
    protected abstract suspend fun upsertSlots(slots: List<ScheduleSlotEntity>)

    // Slots are removed too, through the foreign key cascade.
    @Query("DELETE FROM schedule_phases WHERE medicineId = :medicineId")
    protected abstract suspend fun deletePhasesOf(medicineId: String)

    /** Saves a medicine and replaces its whole schedule in one transaction. */
    @Transaction
    open suspend fun save(medicine: MedicineEntity, phases: List<PhaseWithSlots>) {
        upsertMedicine(medicine)
        deletePhasesOf(medicine.id)
        upsertPhases(phases.map { it.phase })
        upsertSlots(phases.flatMap { it.slots })
    }
}