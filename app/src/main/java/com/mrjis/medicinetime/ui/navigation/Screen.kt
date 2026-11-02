package com.mrjis.medicinetime.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mrjis.medicinetime.R

enum class Screen(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
) {
    Today("today", R.string.nav_today, R.drawable.ic_nav_today),
    Medicines("medicines", R.string.nav_medicines, R.drawable.ic_nav_medicines),
    History("history", R.string.nav_history, R.drawable.ic_nav_history),
    Settings("settings", R.string.nav_settings, R.drawable.ic_nav_settings)
}