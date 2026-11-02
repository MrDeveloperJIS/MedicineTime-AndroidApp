package com.mrjis.medicinetime.ui.components

import androidx.annotation.StringRes
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.domain.model.Meal
import com.mrjis.medicinetime.domain.model.MedicineForm

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