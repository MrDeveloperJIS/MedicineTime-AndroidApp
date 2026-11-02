package com.mrjis.medicinetime.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mrjis.medicinetime.domain.model.DoseSource
import com.mrjis.medicinetime.domain.model.DoseStatus

/**
 * The id is deterministic (medicineId + scheduledAt) so the same dose can never be logged twice,
 * even from two devices. For AS_NEEDED doses, scheduledAt is the moment the dose was logged.
 */
@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId"), Index("medicineId"), Index("scheduledAt")]
)
data class DoseLogEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val medicineId: String,
    val source: DoseSource,
    val scheduledAt: Long,
    val status: DoseStatus,
    val actionAt: Long? = null,
    val snoozeCount: Int = 0,
    val doseAmountSnapshot: Double,
    val updatedAt: Long
) {
    companion object {
        fun idFor(medicineId: String, scheduledAt: Long): String = "$medicineId-$scheduledAt"
    }
}