package com.foodtruckgalaxy.ftg_radar_native

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build

internal object RadarLocationWakeRegistration {
    private const val REQUEST_CODE = 22108
    private const val MIN_TIME_MS = 5_000L
    private const val MIN_DISTANCE_M = 10f

    fun register(context: Context, locationManager: LocationManager): Int {
        val wakeIntent = pendingIntent(context)
        var registeredProviders = 0

        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    MIN_TIME_MS,
                    MIN_DISTANCE_M,
                    wakeIntent,
                )
                registeredProviders += 1
            } catch (_: RuntimeException) {
                // The service reports the aggregate failure when no provider can be registered.
            }
        }

        return registeredProviders
    }

    fun unregister(context: Context) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching { manager.removeUpdates(pendingIntent(context)) }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RadarLocationReceiver::class.java).apply {
            action = RadarLocationReceiver.ACTION_LOCATION
        }
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
    }
}
