package com.mrjis.medicinetime.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.mrjis.medicinetime.MainActivity
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.domain.schedule.DueGroup
import com.mrjis.medicinetime.ui.components.labelRes
import com.mrjis.medicinetime.util.formatAmount

object AlarmNotifications {

    const val CHANNEL_DOSE_DUE = "dose_due"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_DOSE_DUE,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alarm_channel_description)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** One notification per profile; a new alarm for the same profile replaces the old one. */
    fun showDue(context: Context, profile: ProfileEntity, group: DueGroup) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val lines = group.doses.joinToString("\n") { dose ->
            context.getString(
                R.string.alarm_dose_line,
                dose.medicineName,
                formatAmount(dose.doseAmount),
                context.getString(dose.doseUnit.labelRes())
            )
        }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_DOSE_DUE)
            .setSmallIcon(R.drawable.ic_nav_medicines)
            .setContentTitle(context.getString(R.string.alarm_notification_title, profile.name))
            .setContentText(lines.lineSequence().first())
            .setStyle(Notification.BigTextStyle().bigText(lines))
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(profile.id.hashCode(), notification)
    }
}