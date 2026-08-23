package com.foodtruckgalaxy.ftg_radar_native

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarManifestContractTest {
    @Test
    fun `declares process wake receivers and package replacement recovery`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("RadarGeofenceReceiver"))
        assertTrue(manifest.contains("RadarRestoreReceiver"))
        assertTrue(manifest.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(manifest.contains("android.intent.action.MY_PACKAGE_REPLACED"))
    }

    @Test
    fun `geofence path does not expose a foreground worker service`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertFalse(manifest.contains("androidx.work.impl.foreground.SystemForegroundService"))
    }
}
