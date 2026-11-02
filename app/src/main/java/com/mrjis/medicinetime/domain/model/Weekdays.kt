package com.mrjis.medicinetime.domain.model

import java.time.DayOfWeek

/** Weekday bitmask helpers. Bit 0 = Monday ... bit 6 = Sunday. */
object Weekdays {
    const val ALL: Int = 0b1111111

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun maskOf(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or bit(d) }

    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0

    fun toSet(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filter { contains(mask, it) }.toSet()
}