package com.foodtruckgalaxy.ftg_radar_native

import android.util.Log

internal object RadarLog {
    const val TAG = "FTG_RADAR_GEOFENCE"

    fun info(message: String) = Log.i(TAG, message)
    fun warning(message: String, error: Throwable? = null) = Log.w(TAG, message, error)
}
