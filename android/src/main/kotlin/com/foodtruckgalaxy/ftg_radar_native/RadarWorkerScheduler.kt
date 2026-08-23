package com.foodtruckgalaxy.ftg_radar_native

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal object RadarWorkerScheduler {
    internal const val DELIVERY_WORK = "ftg-radar-geofence-delivery"
    private const val RESTORE_WORK = "ftg-radar-geofence-restore"

    fun enqueueDelivery(context: Context) {
        val request = OneTimeWorkRequestBuilder<RadarDeliveryWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        RadarLog.info("delivery_schedule_requested work_id=${request.id}")
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                DELIVERY_WORK,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        RadarLog.info("delivery_work_enqueued work_id=${request.id} policy=APPEND_OR_REPLACE")
    }

    fun enqueueRestore(context: Context, cause: String) {
        val request = OneTimeWorkRequestBuilder<RadarRestoreWorker>()
            .setInputData(Data.Builder().putString("cause", cause).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(RESTORE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(DELIVERY_WORK)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(RESTORE_WORK)
    }
}
