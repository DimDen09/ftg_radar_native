package com.foodtruckgalaxy.ftg_radar_native

import android.content.Context
import android.content.Intent
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class FtgRadarNativePlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
    companion object {
        private const val CHANNEL = "com.foodtruckgalaxy/radar_service"
    }

    private lateinit var context: Context
    private lateinit var channel: MethodChannel

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, CHANNEL)
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startRadar" -> startRadar(call, result)

            "stopRadar" -> {
                RadarWorkerScheduler.cancel(context)
                RadarGeofenceRegistrar.removeBlocking(context)
                RadarEventStore(context).clear()
                RadarGeofenceState.disable(context)
                RadarLocationService.clearConfiguration(context)
                context.stopService(Intent(context, RadarLocationService::class.java))
                result.success(null)
            }

            "isRadarRunning" -> result.success(RadarGeofenceState.isEnabled(context))

            "getRadarStatus" -> result.success(
                RadarGeofenceState.status(context) + mapOf(
                    "legacyForegroundServiceRunning" to RadarLocationService.isRunning(),
                ),
            )

            else -> result.notImplemented()
        }
    }

    private fun startRadar(call: MethodCall, result: MethodChannel.Result) {
        val config = try {
            RadarConfig.from(
                call.argument<String>("token"),
                call.argument<String>("endpoint"),
            )
        } catch (exception: IllegalArgumentException) {
            result.error("RADAR_CONFIG", exception.message, null)
            return
        }

        // The historical foreground service deliberately remains compiled for
        // comparison, but the production Radar path no longer starts it.
        RadarGeofenceStarter.start(context, config) { outcome ->
            outcome.fold(
                onSuccess = result::success,
                onFailure = { error -> result.error("RADAR_START", error.message, null) },
            )
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}
