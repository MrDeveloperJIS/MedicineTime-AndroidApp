package com.mrjis.medicinetime.ui.profile

/** Avatar colors a profile can use (ARGB ints). */
object ProfileColors {
    val all: List<Int> = listOf(
        0xFF2A4FD6.toInt(), // royal blue
        0xFF0B1F3A.toInt(), // navy
        0xFF2F855A.toInt(), // green
        0xFFB7791F.toInt(), // amber
        0xFFC53030.toInt(), // red
        0xFF6B46C1.toInt(), // purple
        0xFF0987A0.toInt(), // teal
        0xFF6B7280.toInt()  // gray
    )

    val default: Int = all.first()
}