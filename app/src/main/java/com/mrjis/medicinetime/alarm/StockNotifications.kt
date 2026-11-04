package com.mrjis.medicinetime.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mrjis.medicinetime.MainActivity
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.domain.model.DoseUnit
import com.mrjis.medicinetime.domain.model.StockUnit
import com.mrjis.medicinetime.domain.stock.StockWarning
import com.mrjis.medicinetime.domain.stock.StockWarningKind
import com.mrjis.medicinetime.util.formatAmount

/**
 * Low-stock notifications. A normal-priority channel on purpose: a stock warning is useful,
 * but it must never ring or pop over the screen like a dose alarm.
 */
object StockNotifications {

    const val CHANNEL_STOCK = "stock_warnings"

    private const val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /** Safe to call many times: Android ignores a channel that already exists. */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_STOCK,
            context.getString(R.string.stock_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.stock_channel_description)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * false when the app's notifications, or this channel, are switched off. The daily check
     * must not run then, or it would mark warnings as "sent" that nobody saw.
     */
    fun canNotify(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL_STOCK)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** One notification per medicine: a more serious warning replaces the earlier one. */
    fun notificationId(medicineId: String): Int =
        "stock:$medicineId".hashCode().let { if (it == 0) 1 else it }

    fun show(context: Context, warning: StockWarning) {
        val medicine = warning.medicine
        val name = medicine.name
        val amount = amountText(context, warning.current, medicine.doseUnit, medicine.stockUnit)

        val title: String
        val text: String
        when (warning.kind) {
            StockWarningKind.DAYS_LEFT -> {
                val days = warning.daysLeft ?: 0
                title = context.getString(R.string.stock_notif_title_low, name)
                text = context.resources.getQuantityString(
                    R.plurals.stock_notif_text_days, days, days, amount
                )
            }

            StockWarningKind.FINISHES_TODAY -> {
                title = context.getString(R.string.stock_notif_title_today, name)
                text = context.getString(R.string.stock_notif_text_today, amount)
            }

            StockWarningKind.OUT -> {
                title = context.getString(R.string.stock_notif_title_not_enough, name)
                text = context.getString(R.string.stock_notif_text_not_enough, amount)
            }

            StockWarningKind.AS_NEEDED_LOW -> {
                title = context.getString(R.string.stock_notif_title_low, name)
                text = context.getString(R.string.stock_notif_text_as_needed_low, amount)
            }

            StockWarningKind.AS_NEEDED_OUT -> {
                title = context.getString(R.string.stock_notif_title_out, name)
                text = context.getString(R.string.stock_notif_text_as_needed_out)
            }
        }

        val id = notificationId(medicine.id)
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java), PI_FLAGS
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_STOCK)
            .setSmallIcon(R.drawable.ic_nav_medicines)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText(context.getString(R.string.stock_notif_for_profile, warning.profile.name))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    /** "12 tablets", "1 tablet", "150 ml". Same wording as the Medicines list. */
    private fun amountText(
        context: Context,
        amount: Double,
        doseUnit: DoseUnit,
        stockUnit: StockUnit
    ): String {
        val number = formatAmount(amount)
        if (stockUnit == StockUnit.ML) {
            return context.getString(R.string.stock_amount_ml, number)
        }
        // Only exactly 1 is singular; 0.5 and 2 are plural.
        val quantity = if (amount == 1.0) 1 else 2
        val plural = when (doseUnit) {
            DoseUnit.DROP -> R.plurals.stock_count_drop
            DoseUnit.UNIT -> R.plurals.stock_count_unit
            else -> R.plurals.stock_count_tablet
        }
        return context.resources.getQuantityString(plural, quantity, number)
    }
}