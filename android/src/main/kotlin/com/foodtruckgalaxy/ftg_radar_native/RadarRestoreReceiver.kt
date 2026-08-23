package com.foodtruckgalaxy.ftg_radar_native

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RadarRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!RadarGeofenceState.isEnabled(context)) return
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> RadarWorkerScheduler.enqueueRestore(
                context,
                intent.action.orEmpty(),
            ).also { RadarLog.info("restore_scheduled cause=${intent.action}") }
        }
    }
}
