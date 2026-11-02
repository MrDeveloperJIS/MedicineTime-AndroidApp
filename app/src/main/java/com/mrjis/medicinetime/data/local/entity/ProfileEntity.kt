package com.mrjis.medicinetime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mrjis.medicinetime.domain.model.Meal
import java.time.LocalTime

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarColor: Int,
    val breakfastTime: LocalTime = LocalTime.of(8, 0),
    val lunchTime: LocalTime = LocalTime.of(13, 0),
    val snacksTime: LocalTime = LocalTime.of(17, 0),
    val dinnerTime: LocalTime = LocalTime.of(20, 0),
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
) {
    fun mealTime(meal: Meal): LocalTime = when (meal) {
        Meal.BREAKFAST -> breakfastTime
        Meal.LUNCH -> lunchTime
        Meal.SNACKS -> snacksTime
        Meal.DINNER -> dinnerTime
    }
}