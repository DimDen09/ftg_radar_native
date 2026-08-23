package com.foodtruckgalaxy.ftg_radar_native

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal class RadarDeliveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {
    override fun doWork(): Result {
        if (!RadarGeofenceState.isEnabled(applicationContext)) return Result.success()
        val store = RadarEventStore(applicationContext)
        repeat(MAX_EVENTS_PER_RUN) {
            val event = store.peek() ?: return Result.success()
            store.markAttempt(event.id)
            when (val outcome = deliver(event, store.size())) {
                is DeliveryOutcome.Retry -> {
                    RadarLog.warning("backend_retry type=${event.type} reason=${outcome.reason}")
                    RadarGeofenceState.recordFailure(applicationContext, outcome.reason)
                    return Result.retry()
                }
                DeliveryOutcome.AuthenticationRevoked -> {
                    RadarLog.warning("backend_authentication_revoked")
                    store.acknowledge(event.id)
                    RadarGeofenceState.disable(applicationContext)
                    return Result.failure()
                }
                is DeliveryOutcome.Success -> {
                    RadarLog.info("backend_delivery type=${event.type}")
                    if (event.type == RadarTelemetry.REGISTRATION ||
                        event.type == RadarTelemetry.SENTINEL_EXIT
                    ) {
                        val plan = outcome.plan ?: return Result.retry()
                        try {
                            RadarGeofenceRegistrar.registerBlocking(applicationContext, plan)
                        } catch (exception: Exception) {
                            val code = exception.message ?: exception.javaClass.simpleName
                            RadarGeofenceState.recordFailure(applicationContext, code)
                            if (event.attempts == 0) {
                                store.append(
                                    RadarQueuedEvent.create(
                                        type = RadarTelemetry.REARM,
                                        geofenceId = RadarGeofenceSpec.SENTINEL_ID,
                                        latitude = event.latitude,
                                        longitude = event.longitude,
                                        accuracy = event.accuracy,
                                        detail = "failed:$code",
                                    ),
                                )
                            }
                            RadarLog.warning("rearm_failed code=$code", exception)
                            return Result.retry()
                        }
                        store.append(
                            RadarQueuedEvent.create(
                                type = RadarTelemetry.REARM,
                                geofenceId = RadarGeofenceSpec.SENTINEL_ID,
                                latitude = event.latitude,
                                longitude = event.longitude,
                                accuracy = event.accuracy,
                                detail = "registered_count=${plan.size}",
                            ),
                        )
                        RadarLog.info("rearm_success count=${plan.size}")
                    }
                    store.acknowledge(event.id)
                }
            }
        }
        return if (store.size() == 0) Result.success() else Result.retry()
    }

    private fun deliver(event: RadarQueuedEvent, queueDepth: Int): DeliveryOutcome {
        val prefs = applicationContext.getSharedPreferences(
            RadarLocationService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val token = prefs.getString(RadarLocationService.KEY_TOKEN, null).orEmpty()
        val endpoint = prefs.getString(RadarLocationService.KEY_ENDPOINT, null).orEmpty()
        if (token.isBlank() || endpoint.isBlank()) return DeliveryOutcome.AuthenticationRevoked

        var connection: HttpURLConnection? = null
        return try {
            val payload = JSONObject().apply {
                put("token", token)
                put("event", event.type)
                put("geofence_id", event.geofenceId ?: JSONObject.NULL)
                put("captured_at", RadarTimestamp.format(event.capturedAtMillis))
                put("source", "android-geofencing-client-v1")
                put("queue_depth", queueDepth)
                put("has_triggering_location", event.hasTriggeringLocation)
                put("detail", event.detail ?: JSONObject.NULL)
                put("is_service_running", false)
                if (event.hasTriggeringLocation) {
                    put("lat", event.latitude)
                    put("lng", event.longitude)
                    if (event.accuracy != null) put("accuracy", event.accuracy.toDouble())
                }
            }
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-FTG-Radar", "android-geofencing-client-v1")
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            RadarLocationService.recordHttpResult(applicationContext, code)
            if (code == 401 || code == 403) return DeliveryOutcome.AuthenticationRevoked
            if (code !in 200..299) return DeliveryOutcome.Retry("http_status_$code")
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            DeliveryOutcome.Success(parsePlan(JSONObject(response)))
        } catch (exception: Exception) {
            DeliveryOutcome.Retry("delivery_${exception.javaClass.simpleName}")
        } finally {
            connection?.disconnect()
        }
    }

    private fun parsePlan(response: JSONObject): List<RadarGeofenceSpec>? {
        val json = response.optJSONObject("geofence_plan") ?: return null
        val centerLat = json.optDouble("center_lat", Double.NaN)
        val centerLng = json.optDouble("center_lng", Double.NaN)
        val sentinelRadius = json.optDouble("sentinel_radius_m", 1_500.0).toFloat()
        val trucksJson = json.optJSONArray("trucks") ?: return null
        val candidates = mutableListOf<RadarGeofenceSpec>()
        for (index in 0 until trucksJson.length()) {
            val truck = trucksJson.optJSONObject(index) ?: continue
            val id = truck.optString("truck_id")
            if (id.isBlank()) continue
            candidates += RadarGeofenceSpec.truck(
                id = id,
                latitude = truck.optDouble("lat", Double.NaN),
                longitude = truck.optDouble("lng", Double.NaN),
                radiusMeters = truck.optDouble("radius_m", sentinelRadius.toDouble()).toFloat(),
                distanceMeters = truck.optDouble("distance_m", Double.MAX_VALUE),
            )
        }
        return RadarGeofencePlan.create(centerLat, centerLng, sentinelRadius, candidates)
            .takeIf { it.isNotEmpty() }
    }

    private sealed interface DeliveryOutcome {
        data class Success(val plan: List<RadarGeofenceSpec>?) : DeliveryOutcome
        data class Retry(val reason: String) : DeliveryOutcome
        data object AuthenticationRevoked : DeliveryOutcome
    }

    companion object {
        private const val MAX_EVENTS_PER_RUN = 20
    }
}
