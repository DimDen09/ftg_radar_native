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
            val queued = RadarQueuedEvent.create(
                type = RadarTelemetry.GEOFENCE_NOT_AVAILABLE,
                geofenceId = null,
                latitude = location?.latitude,
                longitude = location?.longitude,
                accuracy = location?.accuracy,
                detail = GeofenceStatusCodes.getStatusCodeString(event.errorCode),
            )
            RadarEventStore(context).append(queued)
            RadarWorkerScheduler.enqueueDelivery(context)
            RadarLog.warning("callback_error code=${event.errorCode}")
            return
        }

        val specs = RadarGeofenceState.registered(context).associateBy { it.requestId }
        val presence = RadarLocalPresenceStore(context)
        val store = RadarEventStore(context)
        var persisted = 0

        event.triggeringGeofences.orEmpty().forEach { fence ->
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

            var localNotificationShown = false
            when (type) {
                RadarTelemetry.TRUCK_ENTER -> {
                    val isRealEntry = presence.enterIfOutside(fence.requestId)
                    if (isRealEntry) {
                        specs[fence.requestId]?.let { spec ->
                            localNotificationShown = RadarLocalNotifier.notifyTruckEnter(context, spec)
                        }
                    } else {
                        RadarLog.info("local_notification_suppressed already_inside id=${fence.requestId}")
                    }
                }
                RadarTelemetry.TRUCK_EXIT -> presence.exit(fence.requestId)
            }

            RadarLog.info(
                "transition type=$type id=${fence.requestId} local_notification_shown=$localNotificationShown",
            )
            store.append(
                RadarQueuedEvent.create(
                    type = type,
                    geofenceId = fence.requestId,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    accuracy = location?.accuracy,
                    detail = if (location == null) "triggering_location_missing" else null,
                    localNotificationShown = localNotificationShown,
                ),
            )
            persisted += 1
        }

        if (persisted > 0) {
            RadarLog.info("receiver_events_persisted count=$persisted queue_depth=${store.size()}")
            RadarWorkerScheduler.enqueueDelivery(context)
        }
    }

    companion object {
        const val ACTION_GEOFENCE_EVENT = "com.foodtruckgalaxy.radar.GEOFENCE_EVENT"
    }
}
