package com.mrjis.medicinetime.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.mrjis.medicinetime.MainActivity
import com.mrjis.medicinetime.MedicineTimeApplication
import com.mrjis.medicinetime.R

object AlarmNotifications {

    /**
     * Silent on purpose: [AlarmService] plays the sound itself, on the alarm audio stream.
     * (A channel's sound cannot be changed after it exists, hence a new channel id.)
     */
    const val CHANNEL_DOSE_ALARM = "dose_alarm"
    private const val LEGACY_CHANNEL_DOSE_DUE = "dose_due"

    private const val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_DOSE_DUE)

        val channel = NotificationChannel(
            CHANNEL_DOSE_ALARM,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alarm_channel_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /** One notification per profile; a new alarm for the same profile replaces the old one. */
    fun notificationId(profileId: String): Int =
        profileId.hashCode().let { if (it == 0) 1 else it }

    /** Shown for a moment when the service must enter the foreground before it has any details. */
    fun buildPlaceholder(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_DOSE_ALARM)
            .setSmallIcon(R.drawable.ic_nav_medicines)
            .setContentTitle(context.getString(R.string.alarm_placeholder_title))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /** The ringing notification: full-screen alarm, plus Taken and Snooze buttons. */
    fun buildRinging(context: Context, group: RingGroup): Notification {
        val lines = group.items.joinToString("\n") { it.line(context) }
        val logIds = group.logIds
        val screen = PendingIntent.getActivity(
            context,
            notificationId(group.profileId),
            AlarmActivity.createIntent(context, group.profileId, logIds),
            PI_FLAGS
        )
        // The notification's Snooze button uses the smallest snooze choice from Settings.
        val snoozeMinutes =
            (context.applicationContext as MedicineTimeApplication).container.settingsStore.defaultSnoozeMinutes

        return NotificationCompat.Builder(context, CHANNEL_DOSE_ALARM)
            .setSmallIcon(R.drawable.ic_nav_medicines)
            .setContentTitle(context.getString(R.string.alarm_notification_title, group.profileName))
            .setContentText(lines.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
            .setWhen(group.scheduledAtMillis)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .addAction(
                0,
                context.getString(R.string.alarm_taken),
                actionIntent(context, group.profileId, logIds, DoseActionReceiver.ACTION_TAKEN, "taken", 0)
            )
            .addAction(
                0,
                context.getString(R.string.alarm_action_snooze, snoozeMinutes),
                actionIntent(
                    context, group.profileId, logIds, DoseActionReceiver.ACTION_SNOOZE, "snooze", snoozeMinutes
                )
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Fallback when the foreground service could not start: still show the alarm notification. */
    fun show(context: Context, group: RingGroup) {
        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId(group.profileId), buildRinging(context, group))
    }

    private fun actionIntent(
        context: Context,
        profileId: String,
        logIds: List<String>,
        action: String,
        kind: String,
        snoozeMinutes: Int
    ): PendingIntent {
        val intent = Intent(context, DoseActionReceiver::class.java).apply {
            this.action = action
            // A different data Uri per profile and button keeps the pending intents separate.
            data = Uri.parse("medicinetime://dose/$kind/$profileId")
            putExtra(AlarmScheduler.EXTRA_PROFILE_ID, profileId)
            putExtra(AlarmService.EXTRA_LOG_IDS, logIds.toTypedArray())
            putExtra(DoseActionReceiver.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }
        return PendingIntent.getBroadcast(context, notificationId(profileId), intent, PI_FLAGS)
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PI_FLAGS)
}