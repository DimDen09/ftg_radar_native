package com.foodtruckgalaxy.ftg_radar_native

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RadarLocationWakeRegistrationTest {
    @Test
    fun `registers durable pending intent updates for every location provider`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )
        val manager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val registeredProviders = RadarLocationWakeRegistration.register(application, manager)

        assertEquals(2, registeredProviders)
        assertTrue(
            shadowOf(manager)
                .getLocationUpdatePendingIntents(LocationManager.GPS_PROVIDER)
                .isNotEmpty(),
        )
        assertTrue(
            shadowOf(manager)
                .getLocationUpdatePendingIntents(LocationManager.NETWORK_PROVIDER)
                .isNotEmpty(),
        )

        val pendingIntent = shadowOf(manager)
            .getLocationUpdatePendingIntents(LocationManager.GPS_PROVIDER)
            .single()
        assertEquals(
            RadarLocationReceiver::class.java.name,
            shadowOf(pendingIntent).savedIntent.component?.className,
        )
    }

    @Test
    fun `explicit radar stop unregisters durable location wakeups`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        RadarLocationWakeRegistration.register(application, manager)

        RadarLocationService.clearConfiguration(application)

        assertTrue(
            shadowOf(manager)
                .getLocationUpdatePendingIntents(LocationManager.GPS_PROVIDER)
                .isEmpty(),
        )
        assertTrue(
            shadowOf(manager)
                .getLocationUpdatePendingIntents(LocationManager.NETWORK_PROVIDER)
                .isEmpty(),
        )
    }
}
