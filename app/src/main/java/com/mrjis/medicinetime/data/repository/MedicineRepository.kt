package com.mrjis.medicinetime.data.repository

import com.mrjis.medicinetime.data.local.dao.MedicineDao
import com.mrjis.medicinetime.data.local.entity.MedicineEntity
import com.mrjis.medicinetime.data.local.entity.MedicineWithSchedule
import com.mrjis.medicinetime.data.local.entity.PhaseWithSlots
import com.mrjis.medicinetime.domain.model.ScheduleMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

class MedicineRepository(private val dao: MedicineDao) {

    fun observeForProfile(profileId: String): Flow<List<MedicineWithSchedule>> =
        dao.observeForProfile(profileId).map { list -> list.map { it.sorted() } }

    fun observe(id: String): Flow<MedicineWithSchedule?> =
        dao.observe(id).map { it?.sorted() }

    suspend fun get(id: String): MedicineWithSchedule? = dao.get(id)?.sorted()

    /**
     * Saves a medicine and replaces its whole schedule.
     * As-needed medicines never keep phases. Phases are re-numbered by start date.
     */
    suspend fun save(medicine: MedicineEntity, phases: List<PhaseWithSlots>) {
        val cleaned = if (medicine.scheduleMode == ScheduleMode.AS_NEEDED) {
            emptyList()
        } else {
            phases.sortedBy { it.phase.startDate }.mapIndexed { index, item ->
                PhaseWithSlots(
                    phase = item.phase.copy(medicineId = medicine.id, phaseOrder = index),
                    slots = item.slots.map { it.copy(phaseId = item.phase.id) }
                )
            }
        }
        dao.save(medicine.copy(name = medicine.name.trim(), updatedAt = System.currentTimeMillis()), cleaned)
    }

    suspend fun delete(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    private fun MedicineWithSchedule.sorted(): MedicineWithSchedule = copy(
        phases = phases
            .sortedBy { it.phase.phaseOrder }
            .map { p -> p.copy(slots = p.slots.sortedBy { it.clockTime ?: LocalTime.MAX }) }
    )
}