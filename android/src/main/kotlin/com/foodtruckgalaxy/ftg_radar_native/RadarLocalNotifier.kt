package com.foodtruckgalaxy.ftg_radar_native

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal object RadarLocalNotifier {
    private const val CHANNEL_ID = "ftg_radar_local_v1"
    private const val CHANNEL_NAME = "Radar Food Truck Galaxy"
    private const val NOTIFICATION_ID_BASE = 48_000

    fun notifyTruckEnter(context: Context, spec: RadarGeofenceSpec): Boolean {
        val appContext = context.applicationContext
        val truckName = spec.truckName?.trim().orEmpty()
        if (truckName.isEmpty()) {
            RadarLog.warning("local_notification_skipped missing_truck_name id=${spec.requestId}")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            RadarLog.warning("local_notification_skipped notification_permission_missing")
            return false
        }

        return runCatching {
            ensureChannel(appContext)
            val launchIntent = appContext.packageManager
                .getLaunchIntentForPackage(appContext.packageName)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            val contentIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    appContext,
                    spec.requestId.hashCode(),
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

            val icon = appContext.applicationInfo.icon.takeIf { it != 0 }
                ?: android.R.drawable.ic_dialog_map
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle("Un food truck correspondant ? vos go?ts est proche")
                .setContentText("Vous passez pr?s de $truckName.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Vous passez pr?s de $truckName."),
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .build()

            val id = NOTIFICATION_ID_BASE + (spec.requestId.hashCode() and 0x0fffffff)
            NotificationManagerCompat.from(appContext).notify(id, notification)
            RadarLog.info("local_notification_shown id=${spec.requestId} name=$truckName")
            true
        }.getOrElse { error ->
            RadarLog.warning("local_notification_failed id=${spec.requestId}", error)
            false
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alertes imm?diates lorsque vous entrez dans le radar d?un food truck."
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
