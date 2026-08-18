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
            "startRadar" -> {
                val token = call.argument<String>("token")?.trim().orEmpty()
                val endpoint = call.argument<String>("endpoint")?.trim().orEmpty()
                if (token.isEmpty() || endpoint.isEmpty()) {
                    result.error("RADAR_CONFIG", "token/endpoint manquant", null)
                    return
                }

                val intent = Intent(context, RadarLocationService::class.java).apply {
                    action = RadarLocationService.ACTION_START
                    putExtra(RadarLocationService.EXTRA_TOKEN, token)
                    putExtra(RadarLocationService.EXTRA_ENDPOINT, endpoint)
                }
                ContextCompat.startForegroundService(context, intent)
                result.success("started")
            }

            "stopRadar" -> {
                context.getSharedPreferences(
                    RadarLocationService.PREFS_NAME,
                    Context.MODE_PRIVATE
                ).edit().clear().apply()
                context.stopService(Intent(context, RadarLocationService::class.java))
                result.success(null)
            }

            "isRadarRunning" -> {
                val prefs = context.getSharedPreferences(
                    RadarLocationService.PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                result.success(
                    prefs.getString("token", "").orEmpty().isNotEmpty() &&
                    prefs.getString("endpoint", "").orEmpty().isNotEmpty()
                )
            }

            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}
