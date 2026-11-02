package com.mrjis.medicinetime.ui.medicines

import com.mrjis.medicinetime.data.local.entity.MedicineEntity
import com.mrjis.medicinetime.data.local.entity.MedicineWithSchedule
import com.mrjis.medicinetime.data.local.entity.PhaseWithSlots
import com.mrjis.medicinetime.data.local.entity.SchedulePhaseEntity
import com.mrjis.medicinetime.data.local.entity.ScheduleSlotEntity
import com.mrjis.medicinetime.domain.model.DoseUnit
import com.mrjis.medicinetime.domain.model.Meal
import com.mrjis.medicinetime.domain.model.MedicineForm
import com.mrjis.medicinetime.domain.model.RepeatType
import com.mrjis.medicinetime.domain.model.ScheduleMode
import com.mrjis.medicinetime.domain.model.SlotType
import com.mrjis.medicinetime.domain.model.StockUnit
import com.mrjis.medicinetime.domain.model.Weekdays
import com.mrjis.medicinetime.util.formatAmount
import com.mrjis.medicinetime.util.newId
import java.time.LocalDate
import java.time.LocalTime

/** Default length of a new phase, in days (a new medicine starts as a 30-day course). */
private const val DEFAULT_COURSE_DAYS = 30L

/** Length of a phase added with "Add phase" (a typical taper step). */
private const val NEXT_PHASE_DAYS = 7L

/** Preferred clock times for new slots, tried in order until one is free. */
private val SLOT_TIME_CANDIDATES = listOf(8, 20, 14, 12, 17, 22, 6)

private val AMOUNT_REGEX = Regex("""\d+(\.\d*)?|\.\d+""")

// ---------------------------------------------------------------------------------------------
// Draft models (what the form edits). Numbers are Strings so half-typed values like "0." survive.
// ---------------------------------------------------------------------------------------------

data class SlotDraft(
    val id: String = newId(),
    val slotType: SlotType = SlotType.CLOCK,
    val clockTime: LocalTime = LocalTime.of(8, 0),
    val meal: Meal = Meal.BREAKFAST,
    val offsetMinutes: Int = 30,
    val dose: String = "1"
)

data class PhaseDraft(
    val id: String = newId(),
    val startDate: LocalDate,
    val endDate: LocalDate,
    val repeatType: RepeatType = RepeatType.DAILY,
    val repeatDays: Int = Weekdays.ALL,
    val intervalDays: String = "2",
    val slots: List<SlotDraft> = listOf(SlotDraft())
)

data class MedicineDraft(
    val name: String = "",
    val form: MedicineForm = MedicineForm.TABLET,
    val doseUnit: DoseUnit = DoseUnit.TABLET,
    val scheduleMode: ScheduleMode = ScheduleMode.SCHEDULED,
    /** AS_NEEDED only. */
    val defaultDose: String = "",
    val stock: String = "",
    /** AS_NEEDED only. */
    val lowStockLevel: String = "",
    val notes: String = "",
    val phases: List<PhaseDraft> = listOf(newFirstPhase())
) {

    // ---- phase editing helpers ----

    fun updatePhase(phaseId: String, transform: (PhaseDraft) -> PhaseDraft): MedicineDraft =
        copy(phases = phases.map { if (it.id == phaseId) transform(it) else it })

    /** Adds a phase right after the latest one, copying its repeat settings and slots. */
    fun withNextPhase(): MedicineDraft {
        val last = phases.maxByOrNull { it.endDate }
        val added = last?.nextPhase() ?: newFirstPhase()
        return copy(phases = phases + added)
    }

    /** The last remaining phase can't be removed. */
    fun withoutPhase(phaseId: String): MedicineDraft =
        if (phases.size <= 1) this else copy(phases = phases.filterNot { it.id == phaseId })

    // ---- slot editing helpers ----

    fun updateSlot(phaseId: String, slotId: String, transform: (SlotDraft) -> SlotDraft): MedicineDraft =
        updatePhase(phaseId) { phase ->
            phase.copy(slots = phase.slots.map { if (it.id == slotId) transform(it) else it })
        }

    fun withNewSlot(phaseId: String): MedicineDraft =
        updatePhase(phaseId) { phase ->
            val used = phase.slots.filter { it.slotType == SlotType.CLOCK }.map { it.clockTime }.toSet()
            val hour = SLOT_TIME_CANDIDATES.firstOrNull { LocalTime.of(it, 0) !in used } ?: 8
            phase.copy(slots = phase.slots + SlotDraft(clockTime = LocalTime.of(hour, 0)))
        }

    fun withoutSlot(phaseId: String, slotId: String): MedicineDraft =
        updatePhase(phaseId) { phase ->
            if (phase.slots.size <= 1) phase
            else phase.copy(slots = phase.slots.filterNot { it.id == slotId })
        }

    /** Syrup stock is counted in ml, everything else in pieces/drops/units. */
    val stockUnit: StockUnit
        get() = doseUnit.stockUnit()
}

fun newFirstPhase(today: LocalDate = LocalDate.now()): PhaseDraft =
    PhaseDraft(startDate = today, endDate = today.plusDays(DEFAULT_COURSE_DAYS - 1))

private fun PhaseDraft.nextPhase(): PhaseDraft {
    val start = endDate.plusDays(1)
    return copy(
        id = newId(),
        startDate = start,
        endDate = start.plusDays(NEXT_PHASE_DAYS - 1),
        slots = slots.map { it.copy(id = newId()) }
    )
}

// ---------------------------------------------------------------------------------------------
// Unit rules per form
// ---------------------------------------------------------------------------------------------

fun DoseUnit.stockUnit(): StockUnit = if (mlFactor != null) StockUnit.ML else StockUnit.COUNT

fun MedicineForm.defaultDoseUnit(): DoseUnit = when (this) {
    MedicineForm.TABLET, MedicineForm.CAPSULE -> DoseUnit.TABLET
    MedicineForm.SYRUP, MedicineForm.INJECTION -> DoseUnit.ML
    MedicineForm.DROP -> DoseUnit.DROP
    MedicineForm.OTHER -> DoseUnit.UNIT
}

/** Dose units that make sense for a form. The screen hides the picker when there is only one. */
fun MedicineForm.allowedDoseUnits(): List<DoseUnit> = when (this) {
    MedicineForm.TABLET, MedicineForm.CAPSULE -> listOf(DoseUnit.TABLET)
    MedicineForm.SYRUP -> listOf(DoseUnit.ML, DoseUnit.TSP, DoseUnit.TBSP)
    MedicineForm.INJECTION -> listOf(DoseUnit.ML, DoseUnit.UNIT)
    MedicineForm.DROP -> listOf(DoseUnit.DROP, DoseUnit.ML)
    MedicineForm.OTHER -> listOf(DoseUnit.UNIT, DoseUnit.TABLET, DoseUnit.ML)
}

// ---------------------------------------------------------------------------------------------
// Number parsing (accepts Bangla digits and a decimal comma)
// ---------------------------------------------------------------------------------------------

fun parseAmount(text: String): Double? {
    val normalized = buildString {
        for (c in text.trim()) {
            append(
                when (c) {
                    in '০'..'৯' -> '0' + (c - '০')
                    ',' -> '.'
                    else -> c
                }
            )
        }
    }
    if (!AMOUNT_REGEX.matches(normalized)) return null
    return normalized.toDoubleOrNull()
}

// ---------------------------------------------------------------------------------------------
// Validation
// ---------------------------------------------------------------------------------------------

enum class DraftError {
    NAME_BLANK,
    STOCK_INVALID,
    DEFAULT_DOSE_INVALID,
    LOW_STOCK_INVALID,
    NO_PHASES
}

/** Problems with one phase. [gapBefore] is only a warning; everything else blocks saving. */
data class PhaseIssues(
    val endBeforeStart: Boolean = false,
    val overlapsPrevious: Boolean = false,
    val weekdaysEmpty: Boolean = false,
    val intervalInvalid: Boolean = false,
    val noSlots: Boolean = false,
    val duplicateSlots: Boolean = false,
    val gapBefore: Boolean = false
) {
    val hasBlocking: Boolean
        get() = endBeforeStart || overlapsPrevious || weekdaysEmpty ||
                intervalInvalid || noSlots || duplicateSlots
}

data class DraftValidation(
    val medicineErrors: Set<DraftError>,
    /** Keyed by phase id. */
    val phaseIssues: Map<String, PhaseIssues>,
    /** Slots whose dose is empty, zero or not a number. */
    val invalidSlotIds: Set<String>
) {
    val isValid: Boolean
        get() = medicineErrors.isEmpty() &&
                invalidSlotIds.isEmpty() &&
                phaseIssues.values.none { it.hasBlocking }
}

fun MedicineDraft.validate(): DraftValidation {
    val errors = mutableSetOf<DraftError>()

    if (name.isBlank()) errors += DraftError.NAME_BLANK
    if (stock.isNotBlank() && parseAmount(stock) == null) errors += DraftError.STOCK_INVALID

    if (scheduleMode == ScheduleMode.AS_NEEDED) {
        if (defaultDose.isNotBlank()) {
            val value = parseAmount(defaultDose)
            if (value == null || value <= 0.0) errors += DraftError.DEFAULT_DOSE_INVALID
        }
        if (lowStockLevel.isNotBlank() && parseAmount(lowStockLevel) == null) {
            errors += DraftError.LOW_STOCK_INVALID
        }
        return DraftValidation(errors, emptyMap(), emptySet())
    }

    if (phases.isEmpty()) errors += DraftError.NO_PHASES

    val invalidSlots = mutableSetOf<String>()
    phases.forEach { phase ->
        phase.slots.forEach { slot ->
            val value = parseAmount(slot.dose)
            if (value == null || value <= 0.0) invalidSlots += slot.id
        }
    }

    // Overlap and gap checks run in date order, whatever order the cards are shown in.
    val sorted = phases.sortedWith(compareBy({ it.startDate }, { it.endDate }))
    val issues = mutableMapOf<String, PhaseIssues>()
    sorted.forEachIndexed { index, phase ->
        val previous = sorted.getOrNull(index - 1)
        val interval = phase.intervalDays.trim().toIntOrNull()
        issues[phase.id] = PhaseIssues(
            endBeforeStart = phase.endDate.isBefore(phase.startDate),
            overlapsPrevious = previous != null && !phase.startDate.isAfter(previous.endDate),
            weekdaysEmpty = phase.repeatType == RepeatType.WEEKDAYS && phase.repeatDays == 0,
            intervalInvalid = phase.repeatType == RepeatType.EVERY_N_DAYS &&
                    (interval == null || interval < 1),
            noSlots = phase.slots.isEmpty(),
            duplicateSlots = phase.slots.map { it.duplicateKey() }.let { it.size != it.distinct().size },
            gapBefore = previous != null && phase.startDate.isAfter(previous.endDate.plusDays(1))
        )
    }

    return DraftValidation(errors, issues, invalidSlots)
}

private fun SlotDraft.duplicateKey(): String =
    if (slotType == SlotType.CLOCK) "c$clockTime" else "$slotType-$meal-$offsetMinutes"

// ---------------------------------------------------------------------------------------------
// Conversion: draft <-> database entities
// ---------------------------------------------------------------------------------------------

fun MedicineWithSchedule.toDraft(): MedicineDraft {
    val m = medicine
    val phaseDrafts = phases.map { item ->
        val p = item.phase
        PhaseDraft(
            id = p.id,
            startDate = p.startDate,
            endDate = p.endDate,
            repeatType = p.repeatType,
            repeatDays = p.repeatDays,
            intervalDays = p.intervalDays.toString(),
            slots = item.slots.map { s ->
                SlotDraft(
                    id = s.id,
                    slotType = s.slotType,
                    clockTime = s.clockTime ?: LocalTime.of(8, 0),
                    meal = s.meal ?: Meal.BREAKFAST,
                    offsetMinutes = s.offsetMinutes ?: 30,
                    dose = formatAmount(s.doseAmount)
                )
            }
        )
    }
    return MedicineDraft(
        name = m.name,
        form = m.form,
        doseUnit = m.doseUnit,
        scheduleMode = m.scheduleMode,
        defaultDose = m.defaultDose?.let { formatAmount(it) } ?: "",
        stock = formatAmount(m.stockBaseline),
        lowStockLevel = m.lowStockLevel?.let { formatAmount(it) } ?: "",
        notes = m.notes,
        phases = phaseDrafts.ifEmpty { listOf(newFirstPhase()) }
    )
}

/**
 * Builds the rows to save. Call only when [validate] says the draft is valid.
 * The stock baseline (and its timestamp) only moves when the user actually changed the stock.
 */
fun MedicineDraft.toEntities(
    medicineId: String,
    profileId: String,
    existing: MedicineEntity?,
    now: Long
): Pair<MedicineEntity, List<PhaseWithSlots>> {
    val isAsNeeded = scheduleMode == ScheduleMode.AS_NEEDED
    val newStock = parseAmount(stock) ?: 0.0
    val newStockUnit = stockUnit
    val stockChanged = existing == null ||
            existing.stockBaseline != newStock ||
            existing.stockUnit != newStockUnit

    val medicine = MedicineEntity(
        id = medicineId,
        profileId = profileId,
        name = name.trim(),
        form = form,
        doseUnit = doseUnit,
        scheduleMode = scheduleMode,
        defaultDose = if (isAsNeeded) parseAmount(defaultDose) else null,
        stockBaseline = if (stockChanged) newStock else existing!!.stockBaseline,
        stockBaselineAt = if (stockChanged) now else existing!!.stockBaselineAt,
        stockUnit = newStockUnit,
        lowStockLevel = if (isAsNeeded) parseAmount(lowStockLevel) else null,
        expiryDate = existing?.expiryDate,
        notes = notes.trim(),
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
        deletedAt = null
    )

    val phaseRows = if (isAsNeeded) {
        emptyList()
    } else {
        phases.map { p ->
            PhaseWithSlots(
                phase = SchedulePhaseEntity(
                    id = p.id,
                    medicineId = medicineId,
                    phaseOrder = 0, // the repository re-numbers phases by start date
                    startDate = p.startDate,
                    endDate = p.endDate,
                    repeatType = p.repeatType,
                    repeatDays = if (p.repeatType == RepeatType.WEEKDAYS) p.repeatDays else Weekdays.ALL,
                    intervalDays = if (p.repeatType == RepeatType.EVERY_N_DAYS) {
                        p.intervalDays.trim().toIntOrNull() ?: 1
                    } else 1,
                    anchorDate = p.startDate
                ),
                slots = p.slots.map { s ->
                    val isClock = s.slotType == SlotType.CLOCK
                    ScheduleSlotEntity(
                        id = s.id,
                        phaseId = p.id,
                        slotType = s.slotType,
                        clockTime = if (isClock) s.clockTime else null,
                        meal = if (isClock) null else s.meal,
                        offsetMinutes = if (isClock) null else s.offsetMinutes,
                        doseAmount = parseAmount(s.dose) ?: 1.0
                    )
                }
            )
        }
    }

    return medicine to phaseRows
}