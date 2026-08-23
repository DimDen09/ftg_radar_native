package com.foodtruckgalaxy.ftg_radar_native

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.atomic.AtomicBoolean

internal object RadarGeofenceStarter {
    @SuppressLint("MissingPermission")
    fun start(context: Context, config: RadarConfig, completion: (Result<String>) -> Unit) {
        val appContext = context.applicationContext
        // Migration safety: an installation upgraded from 1.3 may still have
        // the historical service or its LocationManager PendingIntent alive.
        RadarLocationWakeRegistration.unregister(appContext)
        appContext.stopService(Intent(appContext, RadarLocationService::class.java))
        RadarGeofenceState.configure(appContext, config)
        RadarLog.info("geofence_start legacy_service_stopped=true")

        RadarGeofenceRegistrar.permissionProblem(appContext)?.let { problem ->
            persistProblem(appContext, problem)
            completion(Result.failure(RadarRegistrationException(problem)))
            return
        }
        RadarGeofenceRegistrar.locationProblem(appContext)?.let { problem ->
            persistProblem(appContext, problem)
            completion(Result.failure(RadarRegistrationException(problem)))
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(appContext)
        val cancellation = CancellationTokenSource()
        val finished = AtomicBoolean(false)

        fun finish(location: Location?, detail: String?) {
            if (!finished.compareAndSet(false, true)) return
            persistRegistration(appContext, location, detail)
            completion(Result.success("geofence_initializing"))
        }

        fun finishFromLastLocation(detail: String) {
            client.lastLocation.addOnCompleteListener { task ->
                val last = if (task.isSuccessful) task.result else null
                finish(last, if (last == null) "triggering_location_missing:$detail" else detail)
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (!finished.get()) {
                cancellation.cancel()
                finishFromLastLocation("initial_location_timeout_last_known")
            }
        }, INITIAL_LOCATION_TIMEOUT_MS)

        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    finish(location, null)
                } else {
                    finishFromLastLocation("last_known_location")
                }
            }
            .addOnFailureListener { exception ->
                finishFromLastLocation("initial_location_${exception.javaClass.simpleName}")
            }
    }

    private fun persistRegistration(context: Context, location: Location?, detail: String?) {
        RadarEventStore(context).append(
            RadarQueuedEvent.create(
                type = RadarTelemetry.REGISTRATION,
                geofenceId = null,
                latitude = location?.latitude,
                longitude = location?.longitude,
                accuracy = location?.accuracy,
                detail = detail,
            ),
        )
        RadarWorkerScheduler.enqueueDelivery(context)
    }

    private fun persistProblem(context: Context, problem: String) {
        RadarGeofenceState.recordFailure(context, problem)
        RadarEventStore(context).append(
            RadarQueuedEvent.create(problem, null, null, null, null, problem),
        )
        RadarWorkerScheduler.enqueueDelivery(context)
    }

    private const val INITIAL_LOCATION_TIMEOUT_MS = 15_000L
}
