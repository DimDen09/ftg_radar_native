package com.foodtruckgalaxy.ftg_radar_native

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarGeofenceModelsLocalNotificationTest {
    @Test
    fun `truck name survives geofence persistence`() {
        val original = RadarGeofenceSpec.truck(
            id = "abc",
            latitude = 50.0,
            longitude = 4.0,
            radiusMeters = 1_500f,
            distanceMeters = 250.0,
            name = "Burger 1",
        )

        val restored = RadarGeofenceSpec.fromJson(original.toJson())

        assertEquals("Burger 1", restored.truckName)
        assertEquals(original.requestId, restored.requestId)
    }

    @Test
    fun `local notification delivery flag survives queue persistence and defaults false for old events`() {
        val shown = RadarQueuedEvent.create(
            type = RadarTelemetry.TRUCK_ENTER,
            geofenceId = "truck:abc",
            latitude = 50.0,
            longitude = 4.0,
            accuracy = 5f,
            localNotificationShown = true,
        )
        val restoredShown = RadarQueuedEvent.fromJson(shown.toJson())
        assertTrue(restoredShown.localNotificationShown)

        val oldJson = shown.toJson().apply { remove("local_notification_shown") }
        val restoredOld = RadarQueuedEvent.fromJson(oldJson)
        assertFalse(restoredOld.localNotificationShown)
    }
}
