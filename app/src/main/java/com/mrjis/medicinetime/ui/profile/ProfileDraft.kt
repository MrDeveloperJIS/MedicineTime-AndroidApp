package com.mrjis.medicinetime.ui.profile

import com.mrjis.medicinetime.domain.model.Meal
import java.time.LocalTime

/** What the profile form edits. */
data class ProfileDraft(
    val name: String,
    val avatarColor: Int,
    val mealTimes: Map<Meal, LocalTime> = defaultMealTimes()
)

fun defaultMealTimes(): Map<Meal, LocalTime> = mapOf(
    Meal.BREAKFAST to LocalTime.of(8, 0),
    Meal.LUNCH to LocalTime.of(13, 0),
    Meal.SNACKS to LocalTime.of(17, 0),
    Meal.DINNER to LocalTime.of(20, 0)
)