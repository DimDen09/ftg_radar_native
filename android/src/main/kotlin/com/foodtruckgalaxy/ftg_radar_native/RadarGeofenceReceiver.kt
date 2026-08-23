package com.foodtruckgalaxy.ftg_radar_native

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

class RadarGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_GEOFENCE_EVENT || !RadarGeofenceState.isEnabled(context)) return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val location = event.triggeringLocation
        RadarLog.info(
            "callback_received transition=${event.geofenceTransition} location_present=${location != null}",
        )

        if (event.hasError()) {
            persistAndSchedule(
                context,
                RadarQueuedEvent.create(
                    type = RadarTelemetry.GEOFENCE_NOT_AVAILABLE,
                    geofenceId = null,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    accuracy = location?.accuracy,
                    detail = GeofenceStatusCodes.getStatusCodeString(event.errorCode),
                ),
            )
            RadarLog.warning("callback_error code=${event.errorCode}")
            return
        }

        val fences = event.triggeringGeofences.orEmpty()
        fences.forEach { fence ->
            val type = when {
                fence.requestId == RadarGeofenceSpec.SENTINEL_ID &&
                    event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT ->
                    RadarTelemetry.SENTINEL_EXIT
                fence.requestId.startsWith("truck:") &&
                    event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ->
                    RadarTelemetry.TRUCK_ENTER
                fence.requestId.startsWith("truck:") &&
                    event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT ->
                    RadarTelemetry.TRUCK_EXIT
                else -> null
            } ?: return@forEach
            RadarLog.info("transition type=$type id=${fence.requestId}")
            persistAndSchedule(
                context,
                RadarQueuedEvent.create(
                    type = type,
                    geofenceId = fence.requestId,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    accuracy = location?.accuracy,
                    detail = if (location == null) "triggering_location_missing" else null,
                ),
            )
        }
    }

    private fun persistAndSchedule(context: Context, event: RadarQueuedEvent) {
        RadarEventStore(context).append(event)
        RadarWorkerScheduler.enqueueDelivery(context)
    }

    companion object {
        const val ACTION_GEOFENCE_EVENT = "com.foodtruckgalaxy.radar.GEOFENCE_EVENT"
    }
}
