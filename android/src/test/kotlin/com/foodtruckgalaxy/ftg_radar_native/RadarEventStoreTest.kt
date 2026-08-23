package com.foodtruckgalaxy.ftg_radar_native

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RadarEventStoreTest {
    @Test
    fun `persists event before delivery and removes it only after acknowledgement`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = RadarEventStore(context)
        val event = RadarQueuedEvent.create(
            type = RadarTelemetry.TRUCK_ENTER,
            geofenceId = "truck:42",
            latitude = 50.85,
            longitude = 4.35,
            accuracy = 18f,
        )

        store.append(event)

        assertEquals(event.id, RadarEventStore(context).peek()?.id)
        store.acknowledge(event.id)
        assertNull(RadarEventStore(context).peek())
    }

    @Test
    fun `keeps missing triggering location explicit`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val event = RadarQueuedEvent.create(
            type = RadarTelemetry.SENTINEL_EXIT,
            geofenceId = RadarGeofenceSpec.SENTINEL_ID,
            latitude = null,
            longitude = null,
            accuracy = null,
        )

        RadarEventStore(context).append(event)

        val restored = RadarEventStore(context).peek()!!
        assertEquals(false, restored.hasTriggeringLocation)
        assertNull(restored.latitude)
    }
}
