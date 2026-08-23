package com.foodtruckgalaxy.ftg_radar_native

import android.content.Context
import org.json.JSONArray

internal object RadarGeofenceState {
    private const val KEY_ENABLED = "geofence_enabled"
    private const val KEY_SPECS = "geofence_specs_v1"
    private const val KEY_REGISTRATION_STATE = "geofence_registration_state"
    private const val KEY_LAST_REGISTRATION_AT = "geofence_last_registration_at"
    private const val KEY_LAST_REGISTRATION_ERROR = "geofence_last_registration_error"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        RadarLocationService.PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun configure(context: Context, config: RadarConfig) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(RadarLocationService.KEY_TOKEN, config.token)
            .putString(RadarLocationService.KEY_ENDPOINT, config.endpoint)
            .putString(KEY_REGISTRATION_STATE, "initializing")
            .remove(KEY_LAST_REGISTRATION_ERROR)
            .commit()
    }

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

    fun disable(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, false)
            .remove(KEY_SPECS)
            .putString(KEY_REGISTRATION_STATE, "stopped")
            .commit()
    }

    fun saveRegistered(context: Context, specs: List<RadarGeofenceSpec>) {
        require(specs.size <= RadarGeofencePlan.MAX_TOTAL)
        prefs(context).edit()
            .putString(KEY_SPECS, specs.toJsonArray().toString())
            .putString(KEY_REGISTRATION_STATE, "registered")
            .putString(KEY_LAST_REGISTRATION_AT, RadarTimestamp.format(System.currentTimeMillis()))
            .remove(KEY_LAST_REGISTRATION_ERROR)
            .commit()
    }

    fun recordFailure(context: Context, code: String) {
        prefs(context).edit()
            .putString(KEY_REGISTRATION_STATE, "failed")
            .putString(KEY_LAST_REGISTRATION_ERROR, code.take(240))
            .commit()
    }

    fun registered(context: Context): List<RadarGeofenceSpec> = runCatching {
        val array = JSONArray(prefs(context).getString(KEY_SPECS, "[]"))
        List(array.length()) { index -> RadarGeofenceSpec.fromJson(array.getJSONObject(index)) }
            .take(RadarGeofencePlan.MAX_TOTAL)
    }.getOrElse { emptyList() }

    fun status(context: Context): Map<String, Any?> {
        val values = prefs(context)
        val specs = registered(context)
        return linkedMapOf(
            "enabled" to isEnabled(context),
            "mode" to "android_geofencing_client",
            "foregroundServiceRequired" to false,
            "registrationState" to values.getString(KEY_REGISTRATION_STATE, "stopped"),
            "registeredCount" to specs.size,
            "truckCount" to specs.count { it.kind == RadarGeofenceKind.TRUCK },
            "sentinelCount" to specs.count { it.kind == RadarGeofenceKind.SENTINEL },
            "lastRegistrationAt" to values.getString(KEY_LAST_REGISTRATION_AT, null),
            "lastRegistrationError" to values.getString(KEY_LAST_REGISTRATION_ERROR, null),
            "queueDepth" to RadarEventStore(context).size(),
        )
    }
}
