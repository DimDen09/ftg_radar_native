package com.foodtruckgalaxy.ftg_radar_native

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

internal object RadarGeofenceRegistrar {
    private const val TASK_TIMEOUT_SECONDS = 25L
    private const val NOTIFICATION_RESPONSIVENESS_MS = 5_000

    fun permissionProblem(context: Context): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return RadarTelemetry.PERMISSION_REMOVED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return RadarTelemetry.PERMISSION_REMOVED
        return null
    }

    fun locationProblem(context: Context): String? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val enabled = runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
        return if (enabled) null else RadarTelemetry.LOCATION_DISABLED
    }

    fun registerBlocking(context: Context, specs: List<RadarGeofenceSpec>) {
        require(specs.isNotEmpty()) { "geofence plan must contain a sentinel" }
        require(specs.size <= RadarGeofencePlan.MAX_TOTAL) { "Android geofence limit exceeded" }
        permissionProblem(context)?.let { throw RadarRegistrationException(it) }
        locationProblem(context)?.let { throw RadarRegistrationException(it) }

        val client = LocationServices.getGeofencingClient(context.applicationContext)
        val pendingIntent = pendingIntent(context)
        Tasks.await(client.removeGeofences(pendingIntent), TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // Seed before addGeofences so INITIAL_TRIGGER_ENTER cannot create a fake
        // local alert for a truck that the user was already inside when we rearmed.
        RadarLocalPresenceStore(context).seedFromRegistration(specs)

        val geofences = specs.map { spec ->
            val transitions = if (spec.notifyOnExit) {
                Geofence.GEOFENCE_TRANSITION_EXIT
            } else {
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
            }
            Geofence.Builder()
                .setRequestId(spec.requestId)
                .setCircularRegion(spec.latitude, spec.longitude, spec.radiusMeters)
                .setTransitionTypes(transitions)
                .setNotificationResponsiveness(NOTIFICATION_RESPONSIVENESS_MS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .addGeofences(geofences)
            .build()
        Tasks.await(client.addGeofences(request, pendingIntent), TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        RadarGeofenceState.saveRegistered(context, specs)
        RadarLog.info(
            "registration_success total=${specs.size} trucks=${specs.count { it.kind == RadarGeofenceKind.TRUCK }} sentinel=1 responsiveness_ms=$NOTIFICATION_RESPONSIVENESS_MS",
        )
    }

    fun removeBlocking(context: Context) {
        runCatching {
            Tasks.await(
                LocationServices.getGeofencingClient(context.applicationContext)
                    .removeGeofences(pendingIntent(context)),
                TASK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
        }
        RadarLocalPresenceStore(context).clear()
        RadarLog.info("registration_removed")
    }

    fun pendingIntent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            22108,
            Intent(context.applicationContext, RadarGeofenceReceiver::class.java).apply {
                action = RadarGeofenceReceiver.ACTION_GEOFENCE_EVENT
            },
            flags,
        )
    }
}

internal class RadarRegistrationException(message: String) : IllegalStateException(message)
