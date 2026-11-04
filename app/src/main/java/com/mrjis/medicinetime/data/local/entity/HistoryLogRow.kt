package com.mrjis.medicinetime.data.local.entity

import androidx.room.Embedded
import com.mrjis.medicinetime.domain.model.DoseUnit

/**
 * A dose log together with its medicine's name and unit, so History can still show doses of
 * a medicine that was deleted later (deletes are soft, the medicine row stays).
 */
data class HistoryLogRow(
    @Embedded val log: DoseLogEntity,
    val medicineName: String,
    val doseUnit: DoseUnit
)