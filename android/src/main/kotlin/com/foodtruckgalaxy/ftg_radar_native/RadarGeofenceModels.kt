package com.foodtruckgalaxy.ftg_radar_native

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal enum class RadarGeofenceKind { TRUCK, SENTINEL }

internal data class RadarGeofenceSpec(
    val requestId: String,
    val kind: RadarGeofenceKind,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val distanceMeters: Double,
    val notifyOnExit: Boolean,
    val truckName: String?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("request_id", requestId)
        put("kind", kind.name)
        put("lat", latitude)
        put("lng", longitude)
        put("radius_m", radiusMeters.toDouble())
        put("distance_m", distanceMeters)
        put("notify_on_exit", notifyOnExit)
        put("truck_name", truckName ?: JSONObject.NULL)
    }

    companion object {
        const val SENTINEL_ID = "sentinel:ftg"
        private const val MIN_RADIUS_M = 100f

        fun truck(
            id: String,
            latitude: Double,
            longitude: Double,
            radiusMeters: Float,
            distanceMeters: Double,
            name: String? = null,
        ) = RadarGeofenceSpec(
            requestId = "truck:$id",
            kind = RadarGeofenceKind.TRUCK,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters.coerceAtLeast(MIN_RADIUS_M),
            distanceMeters = distanceMeters,
            notifyOnExit = false,
            truckName = name?.trim()?.takeIf { it.isNotEmpty() },
        )

        fun sentinel(latitude: Double, longitude: Double, radiusMeters: Float) =
            RadarGeofenceSpec(
                requestId = SENTINEL_ID,
                kind = RadarGeofenceKind.SENTINEL,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters.coerceAtLeast(MIN_RADIUS_M),
                distanceMeters = 0.0,
                notifyOnExit = true,
                truckName = null,
            )

        fun fromJson(json: JSONObject) = RadarGeofenceSpec(
            requestId = json.getString("request_id"),
            kind = RadarGeofenceKind.valueOf(json.getString("kind")),
            latitude = json.getDouble("lat"),
            longitude = json.getDouble("lng"),
            radiusMeters = json.getDouble("radius_m").toFloat(),
            distanceMeters = json.optDouble("distance_m", 0.0),
            notifyOnExit = json.optBoolean("notify_on_exit", false),
            truckName = json.optString("truck_name")
                .trim()
                .takeIf { it.isNotEmpty() && it != "null" },
        )
    }
}

internal object RadarGeofencePlan {
    const val MAX_TRUCKS = 99
    const val MAX_TOTAL = 100

    fun create(
        centerLatitude: Double,
        centerLongitude: Double,
        sentinelRadiusMeters: Float,
        candidates: List<RadarGeofenceSpec>,
    ): List<RadarGeofenceSpec> {
        if (!validCoordinates(centerLatitude, centerLongitude)) return emptyList()
        val trucks = candidates.asSequence()
            .filter { it.kind == RadarGeofenceKind.TRUCK }
            .filter { validCoordinates(it.latitude, it.longitude) }
            .distinctBy { it.requestId }
            .sortedBy { it.distanceMeters }
            .take(MAX_TRUCKS)
            .toList()
        return trucks + RadarGeofenceSpec.sentinel(
            centerLatitude,
            centerLongitude,
            sentinelRadiusMeters,
        )
    }

    private fun validCoordinates(lat: Double, lng: Double) =
        lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
}

internal object RadarTelemetry {
    const val REGISTRATION = "registration"
    const val SENTINEL_EXIT = "sentinel_exit"
    const val TRUCK_ENTER = "truck_enter"
    const val TRUCK_EXIT = "truck_exit"
    const val BACKEND_DELIVERY = "backend_delivery"
    const val REARM = "rearm"
    const val BOOT_REREGISTER = "boot_reregister"
    const val GEOFENCE_NOT_AVAILABLE = "geofence_not_available"
    const val PERMISSION_REMOVED = "permission_removed"
    const val LOCATION_DISABLED = "location_disabled"
}

internal data class RadarQueuedEvent(
    val id: String,
    val type: String,
    val geofenceId: String?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Float?,
    val capturedAtMillis: Long,
    val attempts: Int,
    val detail: String?,
    val localNotificationShown: Boolean,
) {
    val hasTriggeringLocation: Boolean
        get() = latitude != null && longitude != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("geofence_id", geofenceId ?: JSONObject.NULL)
        put("lat", latitude ?: JSONObject.NULL)
        put("lng", longitude ?: JSONObject.NULL)
        put("accuracy", accuracy?.toDouble() ?: JSONObject.NULL)
        put("captured_at_ms", capturedAtMillis)
        put("attempts", attempts)
        put("detail", detail ?: JSONObject.NULL)
        put("local_notification_shown", localNotificationShown)
    }

    fun withAttempt() = copy(attempts = attempts + 1)

    companion object {
        fun create(
            type: String,
            geofenceId: String?,
            latitude: Double?,
            longitude: Double?,
            accuracy: Float?,
            detail: String? = null,
            localNotificationShown: Boolean = false,
        ) = RadarQueuedEvent(
            id = UUID.randomUUID().toString(),
            type = type,
            geofenceId = geofenceId,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            capturedAtMillis = System.currentTimeMillis(),
            attempts = 0,
            detail = detail,
            localNotificationShown = localNotificationShown,
        )

        fun fromJson(json: JSONObject) = RadarQueuedEvent(
            id = json.getString("id"),
            type = json.getString("type"),
            geofenceId = json.optString("geofence_id").takeIf { it.isNotBlank() },
            latitude = if (json.isNull("lat")) null else json.getDouble("lat"),
            longitude = if (json.isNull("lng")) null else json.getDouble("lng"),
            accuracy = if (json.isNull("accuracy")) null else json.getDouble("accuracy").toFloat(),
            capturedAtMillis = json.getLong("captured_at_ms"),
            attempts = json.optInt("attempts", 0),
            detail = json.optString("detail").takeIf { it.isNotBlank() },
            localNotificationShown = json.optBoolean("local_notification_shown", false),
        )
    }
}

internal fun List<RadarGeofenceSpec>.toJsonArray(): JSONArray =
    JSONArray().also { array -> forEach { array.put(it.toJson()) } }
