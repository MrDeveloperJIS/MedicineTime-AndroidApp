package com.mrjis.medicinetime.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mrjis.medicinetime.MedicineTimeApplication
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException

/**
 * Daily safety net: marks unanswered and never-rung doses as MISSED, even if the app
 * was not opened. The Today screen does not depend on it (it plans missed doses live).
 */
class MissedSweepWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MedicineTimeApplication).container
        return try {
            val changed = container.missedSweeper.sweep()
            Log.i(TAG, "Daily sweep marked $changed dose(s) as missed")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Daily sweep failed (attempt ${runAttemptCount + 1})", e)
            // Retry a few times with backoff; after that wait for tomorrow's run.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val TAG = "MissedSweepWorker"
        private const val UNIQUE_NAME = "missed_dose_sweep"
        private const val MAX_ATTEMPTS = 3

        /** Quiet hour of the day for the sweep (local time). */
        private val RUN_AT: LocalTime = LocalTime.of(4, 0)

        /** Safe to call on every app start: the same unique work is updated, never duplicated. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(RUN_AT)
            if (!next.isAfter(now)) next = next.plusDays(1)

            val request = PeriodicWorkRequestBuilder<MissedSweepWorker>(Duration.ofDays(1))
                .setInitialDelay(Duration.between(now, next))
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}