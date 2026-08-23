package com.foodtruckgalaxy.ftg_radar_native

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

internal object RadarGeofenceStarter {
    @SuppressLint("MissingPermission")
    fun start(context: Context, config: RadarConfig, completion: (Result<String>) -> Unit) {
        val appContext = context.applicationContext
        RadarGeofenceState.configure(appContext, config)

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
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    persistRegistration(appContext, location, null)
                    completion(Result.success("geofence_initializing"))
                } else {
                    client.lastLocation
                        .addOnCompleteListener { lastTask ->
                            val last = if (lastTask.isSuccessful) lastTask.result else null
                            persistRegistration(
                                appContext,
                                last,
                                if (last == null) "triggering_location_missing" else "last_known_location",
                            )
                            completion(Result.success("geofence_initializing"))
                        }
                }
            }
            .addOnFailureListener { exception ->
                persistRegistration(
                    appContext,
                    null,
                    "initial_location_${exception.javaClass.simpleName}",
                )
                completion(Result.success("geofence_initializing"))
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
}
