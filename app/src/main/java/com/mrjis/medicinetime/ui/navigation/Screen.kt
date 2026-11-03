package com.mrjis.medicinetime.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mrjis.medicinetime.R

/** The four bottom-bar destinations. */
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

/** Other destinations, reached from inside the app (no bottom bar). */
object Routes {
    const val PROFILES = "profiles"
    const val SETUP = "setup"

    const val MEDICINE_EDIT = "medicine_edit"
    const val MEDICINE_ID_ARG = "medicineId"
    const val MEDICINE_EDIT_PATTERN = "$MEDICINE_EDIT?$MEDICINE_ID_ARG={$MEDICINE_ID_ARG}"

    /** null id = add a new medicine. */
    fun medicineEdit(id: String? = null): String =
        if (id == null) MEDICINE_EDIT else "$MEDICINE_EDIT?$MEDICINE_ID_ARG=$id"
}