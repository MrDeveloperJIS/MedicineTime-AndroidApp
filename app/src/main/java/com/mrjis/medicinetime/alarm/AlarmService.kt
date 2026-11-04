package com.mrjis.medicinetime.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mrjis.medicinetime.AppContainer
import com.mrjis.medicinetime.MedicineTimeApplication
import com.mrjis.medicinetime.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Rings (sound + vibration) while at least one dose group waits for an answer.
 *
 * One group per profile. The first ringing profile owns the foreground notification;
 * others get a normal notification, and the foreground notification moves on when its owner is answered.
 * The sound stops by itself after [MAX_RING_MILLIS]; the notification stays so the dose can still be answered.
 */
class AlarmService : Service() {

    /** profileId -> ids of the dose logs that are ringing. */
    private val rings = LinkedHashMap<String, Set<String>>()
    private var foregroundProfileId: String? = null
    private var isForeground = false
    private var ringing = false

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val timeout = Runnable { onRingTimeout() }

    private var ringtone: Ringtone? = null
    private var focusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val container: AppContainer
        get() = (application as MedicineTimeApplication).container
    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profileId = intent?.getStringExtra(AlarmScheduler.EXTRA_PROFILE_ID)
        val logIds = intent?.getStringArrayExtra(EXTRA_LOG_IDS)?.toList().orEmpty()
        scope.launch {
            if (profileId == null || logIds.isEmpty()) stopIfIdle() else handleStart(profileId, logIds)
        }
        return START_NOT_STICKY
    }

    private suspend fun handleStart(profileId: String, newLogIds: List<String>) {
        // Same profile ringing again: show old and new doses together.
        val allIds = LinkedHashSet(rings[profileId].orEmpty()).apply { addAll(newLogIds) }.toList()
        val group = RingLoader.load(container, profileId, allIds)
        if (group == null) {
            stopIfIdle()
            return
        }

        val notification = AlarmNotifications.buildRinging(this, group)
        val notificationId = AlarmNotifications.notificationId(profileId)

        if (foregroundProfileId == null) {
            if (!enterForeground(notificationId, notification)) {
                // Could not run as a foreground service: at least show the notification.
                notificationManager.notify(notificationId, notification)
                stopSelf()
                return
            }
            rings[profileId] = LinkedHashSet(group.logIds)
            foregroundProfileId = profileId
            startRinging()
        } else {
            rings[profileId] = LinkedHashSet(group.logIds)
            notificationManager.notify(notificationId, notification)
        }

        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, MAX_RING_MILLIS)
    }

    /** Called when a profile's alarm was answered (taken or snoozed). */
    fun dismissRing(profileId: String) {
        handler.post {
            val removed = rings.remove(profileId) != null
            val notificationId = AlarmNotifications.notificationId(profileId)
            when {
                removed && rings.isEmpty() -> {
                    shutDown()
                    notificationManager.cancel(notificationId)
                }
                removed && foregroundProfileId == profileId ->
                    transferForeground(rings.keys.first(), notificationId)
                else -> notificationManager.cancel(notificationId)
            }
        }
    }

    /** The foreground notification cannot be cancelled while it is the foreground one, so it moves first. */
    private fun transferForeground(toProfileId: String, oldNotificationId: Int) {
        scope.launch {
            val ids = rings[toProfileId]?.toList()
            if (ids == null) {
                // That one was answered meanwhile.
                if (rings.isEmpty()) shutDown()
                return@launch
            }
            val group = RingLoader.load(container, toProfileId, ids)
            val notification = group?.let { AlarmNotifications.buildRinging(this@AlarmService, it) }
                ?: AlarmNotifications.buildPlaceholder(this@AlarmService)
            foregroundProfileId = toProfileId
            enterForeground(AlarmNotifications.notificationId(toProfileId), notification)
            notificationManager.cancel(oldNotificationId)
        }
    }

    private fun stopIfIdle() {
        if (rings.isNotEmpty()) return
        // A service started with startForegroundService() must enter the foreground once, even to quit.
        if (!isForeground) enterForeground(FALLBACK_NOTIFICATION_ID, AlarmNotifications.buildPlaceholder(this))
        shutDown()
    }

    private fun shutDown() {
        stopRinging()
        handler.removeCallbacks(timeout)
        foregroundProfileId = null
        isForeground = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Sound ran its full length. Keep the notifications (with their buttons), end the service. */
    private fun onRingTimeout() {
        stopRinging()
        rings.clear()
        foregroundProfileId = null
        isForeground = false
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun enterForeground(id: Int, notification: Notification): Boolean = try {
        ServiceCompat.startForeground(
            this, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        isForeground = true
        true
    } catch (e: Exception) {
        Log.e(TAG, "Could not enter the foreground", e)
        false
    }

    // ---- Sound, vibration, wake lock -------------------------------------------------------

    private fun startRinging() {
        if (ringing) return
        ringing = true
        acquireWakeLock()
        startSound()
        startVibration()
    }

    private fun stopRinging() {
        if (!ringing) return
        ringing = false
        ringtone?.stop()
        ringtone = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
        vibrator().cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MedicineTime:alarm")
            .apply { acquire(MAX_RING_MILLIS + 10_000L) }
    }

    private fun startSound() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .build()
        getSystemService(AudioManager::class.java).requestAudioFocus(request)
        focusRequest = request

        // The sound chosen in Settings. If it cannot play (file gone, no access), fall back to the
        // system default alarm sound so the alarm is never silent.
        val chosen = container.settingsStore.ringtoneUri.value?.let { runCatching { Uri.parse(it) }.getOrNull() }
        ringtone = playRingtone(chosen, attributes, mustBePlaying = true)
            ?: playRingtone(defaultAlarmUri(), attributes, mustBePlaying = false)
    }

    private fun defaultAlarmUri(): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private fun playRingtone(uri: Uri?, attributes: AudioAttributes, mustBePlaying: Boolean): Ringtone? {
        if (uri == null) return null
        return try {
            val sound = RingtoneManager.getRingtone(this, uri) ?: return null
            sound.audioAttributes = attributes
            sound.isLooping = true
            sound.play()
            if (mustBePlaying && !sound.isPlaying) {
                sound.stop()
                null
            } else {
                sound
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not play the alarm sound", e)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator().vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            vibrator().vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
        }
    }

    private fun vibrator(): Vibrator = getSystemService(VibratorManager::class.java).defaultVibrator

    override fun onDestroy() {
        stopRinging()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        const val EXTRA_LOG_IDS = "logIds"
        private const val FALLBACK_NOTIFICATION_ID = 4242

        /** The sound stops after this long if nobody answers. */
        const val MAX_RING_MILLIS = 5 * 60_000L

        @Volatile
        private var instance: AlarmService? = null

        /** Starts (or adds to) the ringing. Returns false if Android refused to start the service. */
        fun start(context: Context, profileId: String, logIds: Collection<String>): Boolean = try {
            val intent = Intent(context, AlarmService::class.java)
                .putExtra(AlarmScheduler.EXTRA_PROFILE_ID, profileId)
                .putExtra(EXTRA_LOG_IDS, logIds.toTypedArray())
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not start the alarm service", e)
            false
        }

        /** Stops the ringing for one profile and removes its notification. */
        fun dismiss(context: Context, profileId: String) {
            val service = instance
            if (service != null) {
                service.dismissRing(profileId)
            } else {
                context.getSystemService(NotificationManager::class.java)
                    .cancel(AlarmNotifications.notificationId(profileId))
            }
        }
    }
}