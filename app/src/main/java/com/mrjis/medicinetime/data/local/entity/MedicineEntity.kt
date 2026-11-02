package com.mrjis.medicinetime.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mrjis.medicinetime.domain.model.DoseUnit
import com.mrjis.medicinetime.domain.model.MedicineForm
import com.mrjis.medicinetime.domain.model.ScheduleMode
import com.mrjis.medicinetime.domain.model.StockUnit
import java.time.LocalDate

/**
 * Start/end dates live on the schedule phases (a medicine's range is first phase start to
 * last phase end). AS_NEEDED medicines have no phases and no date range.
 *
 * Current stock = [stockBaseline] minus doses taken after [stockBaselineAt] (calculated later).
 */
@Entity(
    tableName = "medicines",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class MedicineEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val form: MedicineForm = MedicineForm.TABLET,
    val doseUnit: DoseUnit = DoseUnit.TABLET,
    val scheduleMode: ScheduleMode = ScheduleMode.SCHEDULED,
    /** AS_NEEDED only: pre-fills the "Take now" sheet. */
    val defaultDose: Double? = null,
    val stockBaseline: Double = 0.0,
    val stockBaselineAt: Long,
    val stockUnit: StockUnit = StockUnit.COUNT,
    val lowStockLevel: Double? = null,
    val expiryDate: LocalDate? = null,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)