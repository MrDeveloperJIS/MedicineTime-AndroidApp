package com.mrjis.medicinetime.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mrjis.medicinetime.MedicineTimeApplication
import com.mrjis.medicinetime.alarm.StockNotifications
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException

/**
 * Once a day (around 09:00) sends the low-stock warnings: 7, 5, 3, 2, 1 days left, the day it
 * runs out, and the as-needed level / zero warnings. What counts as "already sent" is kept by
 * [com.mrjis.medicinetime.domain.stock.StockChecker], so this worker can run any number of
 * times without sending repeats. It needs no internet.
 */
class StockCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MedicineTimeApplication).container

        StockNotifications.createChannel(applicationContext)
        if (!StockNotifications.canNotify(applicationContext)) {
            // Do not run the check: it would mark warnings as sent that nobody could see.
            // They are sent on a later day, once notifications are allowed again.
            Log.i(TAG, "Notifications are off, skipping the stock check")
            return Result.success()
        }

        return try {
            val sent = container.stockChecker.check { warning ->
                StockNotifications.show(applicationContext, warning)
            }
            Log.i(TAG, "Daily stock check sent $sent warning(s)")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Daily stock check failed (attempt ${runAttemptCount + 1})", e)
            // Retry a few times with backoff; after that wait for tomorrow's run.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val TAG = "StockCheckWorker"
        private const val UNIQUE_NAME = "stock_check"
        private const val MAX_ATTEMPTS = 3

        /** Time of day for the check (local time). */
        private val RUN_AT: LocalTime = LocalTime.of(9, 0)

        /** Safe to call on every app start: the same unique work is updated, never duplicated. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(RUN_AT)
            if (!next.isAfter(now)) next = next.plusDays(1)

            val request = PeriodicWorkRequestBuilder<StockCheckWorker>(Duration.ofDays(1))
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