package com.foodtruckgalaxy.ftg_radar_native_example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRadarPermissions()
    }

    private fun requestRadarPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val missing = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.ACCESS_COARSE_LOCATION
            missing += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), RADAR_PERMISSION_REQUEST)
        }
    }

    companion object {
        private const val RADAR_PERMISSION_REQUEST = 22107
    }
}
