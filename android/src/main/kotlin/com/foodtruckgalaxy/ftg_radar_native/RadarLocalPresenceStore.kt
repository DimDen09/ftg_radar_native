package com.foodtruckgalaxy.ftg_radar_native

import android.content.Context

internal class RadarLocalPresenceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        RadarLocationService.PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun seedFromRegistration(specs: List<RadarGeofenceSpec>) {
        synchronized(LOCK) {
            val inside = specs.asSequence()
                .filter { it.kind == RadarGeofenceKind.TRUCK }
                .filter { it.distanceMeters.isFinite() }
                .filter { it.distanceMeters <= it.radiusMeters.toDouble() }
                .map { it.requestId }
                .toSet()
            prefs.edit().putStringSet(KEY_INSIDE, inside).commit()
        }
    }

    /** Returns true only for a real outside -> inside transition. */
    fun enterIfOutside(requestId: String): Boolean {
        if (!requestId.startsWith("truck:")) return false
        synchronized(LOCK) {
            val current = prefs.getStringSet(KEY_INSIDE, emptySet()).orEmpty().toMutableSet()
            if (!current.add(requestId)) return false
            prefs.edit().putStringSet(KEY_INSIDE, current).commit()
            return true
        }
    }

    fun exit(requestId: String) {
        if (!requestId.startsWith("truck:")) return
        synchronized(LOCK) {
            val current = prefs.getStringSet(KEY_INSIDE, emptySet()).orEmpty().toMutableSet()
            if (current.remove(requestId)) {
                prefs.edit().putStringSet(KEY_INSIDE, current).commit()
            }
        }
    }

    fun clear() {
        synchronized(LOCK) {
            prefs.edit().remove(KEY_INSIDE).commit()
        }
    }

    companion object {
        private const val KEY_INSIDE = "geofence_local_inside_v1"
        private val LOCK = Any()
    }
}
