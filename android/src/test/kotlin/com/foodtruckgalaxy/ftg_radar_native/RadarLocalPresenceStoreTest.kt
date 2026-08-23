package com.foodtruckgalaxy.ftg_radar_native

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RadarLocalPresenceStoreTest {
    private lateinit var context: Context
    private lateinit var store: RadarLocalPresenceStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(RadarLocationService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = RadarLocalPresenceStore(context)
    }

    @Test
    fun `initial trigger for an already-inside truck is suppressed but a real reentry notifies`() {
        val inside = RadarGeofenceSpec.truck(
            id = "inside",
            latitude = 50.0,
            longitude = 4.0,
            radiusMeters = 1_000f,
            distanceMeters = 100.0,
            name = "Inside truck",
        )
        val outside = RadarGeofenceSpec.truck(
            id = "outside",
            latitude = 50.1,
            longitude = 4.1,
            radiusMeters = 1_000f,
            distanceMeters = 2_000.0,
            name = "Outside truck",
        )

        store.seedFromRegistration(listOf(inside, outside))

        assertFalse(store.enterIfOutside(inside.requestId))
        assertTrue(store.enterIfOutside(outside.requestId))
        assertFalse(store.enterIfOutside(outside.requestId))

        store.exit(outside.requestId)
        assertTrue(store.enterIfOutside(outside.requestId))
    }
}
