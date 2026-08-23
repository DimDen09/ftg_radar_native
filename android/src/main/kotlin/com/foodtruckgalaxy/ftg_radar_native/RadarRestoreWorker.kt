package com.foodtruckgalaxy.ftg_radar_native

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

internal class RadarRestoreWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {
    override fun doWork(): Result {
        if (!RadarGeofenceState.isEnabled(applicationContext)) return Result.success()
        val cause = inputData.getString("cause").orEmpty()
        val specs = RadarGeofenceState.registered(applicationContext)
        if (specs.isEmpty()) {
            RadarGeofenceState.recordFailure(applicationContext, "restore_plan_missing")
            return Result.retry()
        }

        val problem = RadarGeofenceRegistrar.permissionProblem(applicationContext)
            ?: RadarGeofenceRegistrar.locationProblem(applicationContext)
        if (problem != null) {
            persist(problem, cause)
            RadarGeofenceState.recordFailure(applicationContext, problem)
            RadarWorkerScheduler.enqueueDelivery(applicationContext)
            return Result.failure()
        }

        persist(RadarTelemetry.BOOT_REREGISTER, cause)
        RadarLog.info("restore_started cause=$cause count=${specs.size}")
        return try {
            RadarGeofenceRegistrar.registerBlocking(applicationContext, specs)
            RadarEventStore(applicationContext).append(
                RadarQueuedEvent.create(
                    type = RadarTelemetry.REARM,
                    geofenceId = RadarGeofenceSpec.SENTINEL_ID,
                    latitude = specs.singleOrNull { it.kind == RadarGeofenceKind.SENTINEL }?.latitude,
                    longitude = specs.singleOrNull { it.kind == RadarGeofenceKind.SENTINEL }?.longitude,
                    accuracy = null,
                    detail = "restore_ok:$cause",
                ),
            )
            RadarWorkerScheduler.enqueueDelivery(applicationContext)
            RadarLog.info("restore_success cause=$cause count=${specs.size}")
            Result.success()
        } catch (exception: Exception) {
            RadarGeofenceState.recordFailure(
                applicationContext,
                "restore_${exception.javaClass.simpleName}",
            )
            RadarWorkerScheduler.enqueueDelivery(applicationContext)
            RadarLog.warning("restore_failed cause=$cause", exception)
            Result.retry()
        }
    }

    private fun persist(type: String, detail: String) {
        val sentinel = RadarGeofenceState.registered(applicationContext)
            .singleOrNull { it.kind == RadarGeofenceKind.SENTINEL }
        RadarEventStore(applicationContext).append(
            RadarQueuedEvent.create(
                type = type,
                geofenceId = RadarGeofenceSpec.SENTINEL_ID,
                latitude = sentinel?.latitude,
                longitude = sentinel?.longitude,
                accuracy = null,
                detail = detail,
            ),
        )
    }
}
