package com.foodtruckgalaxy.ftg_radar_native

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build

class RadarLocationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_LOCATION = "com.foodtruckgalaxy.radar.LOCATION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOCATION) return

        val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED, Location::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED)
        } ?: return

        val pendingResult = goAsync()
        if (!RadarLocationSync.enqueue(context, location) { pendingResult.finish() }) {
            pendingResult.finish()
        }
    }
}
