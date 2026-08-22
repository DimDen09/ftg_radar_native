package com.foodtruckgalaxy.ftg_radar_native

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
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
                RadarLocationService.clearConfiguration(context)
                context.stopService(Intent(context, RadarLocationService::class.java))
                result.success(null)
            }

            "isRadarRunning" -> result.success(RadarLocationService.isRunning())

            "getRadarStatus" -> result.success(RadarLocationService.status(context))

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

        val intent = Intent(context, RadarLocationService::class.java).apply {
            action = RadarLocationService.ACTION_START
            putExtra(RadarLocationService.EXTRA_TOKEN, config.token)
            putExtra(RadarLocationService.EXTRA_ENDPOINT, config.endpoint)
        }
        try {
            RadarLocationService.markStarting(context)
            ContextCompat.startForegroundService(context, intent)
            result.success("started")
        } catch (exception: RuntimeException) {
            RadarLocationService.recordStartFailure(context, exception)
            result.error("RADAR_START", exception.message, null)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}
