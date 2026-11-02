package com.mrjis.medicinetime.ui.components

import androidx.annotation.StringRes
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.domain.model.DoseUnit
import com.mrjis.medicinetime.domain.model.Meal
import com.mrjis.medicinetime.domain.model.MedicineForm
import com.mrjis.medicinetime.domain.model.RepeatType
import com.mrjis.medicinetime.domain.model.SlotType

@StringRes
fun Meal.labelRes(): Int = when (this) {
    Meal.BREAKFAST -> R.string.meal_breakfast
    Meal.LUNCH -> R.string.meal_lunch
    Meal.SNACKS -> R.string.meal_snacks
    Meal.DINNER -> R.string.meal_dinner
}

@StringRes
fun MedicineForm.labelRes(): Int = when (this) {
    MedicineForm.TABLET -> R.string.form_tablet
    MedicineForm.CAPSULE -> R.string.form_capsule
    MedicineForm.SYRUP -> R.string.form_syrup
    MedicineForm.INJECTION -> R.string.form_injection
    MedicineForm.DROP -> R.string.form_drop
    MedicineForm.OTHER -> R.string.form_other
}

@StringRes
fun DoseUnit.labelRes(): Int = when (this) {
    DoseUnit.TABLET -> R.string.unit_tablet
    DoseUnit.ML -> R.string.unit_ml
    DoseUnit.TSP -> R.string.unit_tsp
    DoseUnit.TBSP -> R.string.unit_tbsp
    DoseUnit.DROP -> R.string.unit_drop
    DoseUnit.UNIT -> R.string.unit_unit
}

@StringRes
fun SlotType.labelRes(): Int = when (this) {
    SlotType.CLOCK -> R.string.slot_clock
    SlotType.MEAL_BEFORE -> R.string.slot_meal_before
    SlotType.MEAL_AFTER -> R.string.slot_meal_after
}

@StringRes
fun RepeatType.labelRes(): Int = when (this) {
    RepeatType.DAILY -> R.string.schedule_daily
    RepeatType.WEEKDAYS -> R.string.schedule_weekdays
    RepeatType.EVERY_N_DAYS -> R.string.repeat_every_n
}