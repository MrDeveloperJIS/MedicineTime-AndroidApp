package com.mrjis.medicinetime.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mrjis.medicinetime.data.local.entity.DoseLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseLogDao {

    @Upsert
    suspend fun upsert(log: DoseLogEntity)

    @Query("SELECT * FROM dose_logs WHERE id = :id")
    suspend fun get(id: String): DoseLogEntity?

    @Query("SELECT * FROM dose_logs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<DoseLogEntity>

    /** Live version of [getByIds]. The alarm screen uses it so it closes when a dose is answered elsewhere. */
    @Query("SELECT * FROM dose_logs WHERE id IN (:ids)")
    fun observeByIds(ids: List<String>): Flow<List<DoseLogEntity>>

    /**
     * Doses the user snoozed. For a SNOOZED row, actionAt is the moment it should ring again.
     */
    @Query("SELECT * FROM dose_logs WHERE profileId = :profileId AND status = 'SNOOZED'")
    suspend fun getSnoozed(profileId: String): List<DoseLogEntity>

    @Query("DELETE FROM dose_logs WHERE id = :id")
    suspend fun delete(id: String)

    /** Logs in [fromMillis, toMillis) for one profile, oldest first. */
    @Query(
        "SELECT * FROM dose_logs WHERE profileId = :profileId " +
                "AND scheduledAt >= :fromMillis AND scheduledAt < :toMillis ORDER BY scheduledAt"
    )
    fun observeBetween(profileId: String, fromMillis: Long, toMillis: Long): Flow<List<DoseLogEntity>>

    /** Most recent as-needed dose of a medicine ("Last taken: 2:30 PM"). */
    @Query(
        "SELECT * FROM dose_logs WHERE medicineId = :medicineId AND source = 'AS_NEEDED' " +
                "ORDER BY scheduledAt DESC LIMIT 1"
    )
    fun observeLastAsNeeded(medicineId: String): Flow<DoseLogEntity?>
}