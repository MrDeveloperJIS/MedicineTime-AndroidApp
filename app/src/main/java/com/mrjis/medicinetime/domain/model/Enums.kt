package com.mrjis.medicinetime.domain.model

enum class MedicineForm { TABLET, CAPSULE, SYRUP, INJECTION, DROP, OTHER }

enum class StockUnit { COUNT, ML }

/**
 * Unit a single dose is measured in.
 * [mlFactor] is the millilitre value of one unit (1 tsp = 5 ml, 1 tbsp = 15 ml), or null if not a volume.
 */
enum class DoseUnit(val mlFactor: Double?) {
    TABLET(null),
    ML(1.0),
    TSP(5.0),
    TBSP(15.0),
    DROP(null),
    UNIT(null);

    /** How much stock one dose of [amount] uses. Syrup stock is in ml, so 2 tsp subtracts 10. */
    fun toStockAmount(amount: Double, stockUnit: StockUnit): Double =
        if (stockUnit == StockUnit.ML && mlFactor != null) amount * mlFactor else amount
}

enum class ScheduleMode { SCHEDULED, AS_NEEDED }

enum class RepeatType { DAILY, WEEKDAYS, EVERY_N_DAYS }

enum class SlotType { CLOCK, MEAL_BEFORE, MEAL_AFTER }

enum class Meal { BREAKFAST, LUNCH, SNACKS, DINNER }

enum class DoseSource { SCHEDULED, AS_NEEDED }

enum class DoseStatus { PENDING, TAKEN, MISSED, SNOOZED }