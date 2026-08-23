package com.foodtruckgalaxy.ftg_radar_native

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarGeofencePlanTest {
    @Test
    fun `limits Android registrations to 99 trucks and one sentinel`() {
        val trucks = (1..140).map { index ->
            RadarGeofenceSpec.truck(
                id = "truck-$index",
                latitude = 50.0 + index / 10_000.0,
                longitude = 4.0,
                radiusMeters = 1_500f,
                distanceMeters = index.toDouble(),
            )
        }

        val plan = RadarGeofencePlan.create(
            centerLatitude = 50.0,
            centerLongitude = 4.0,
            sentinelRadiusMeters = 1_500f,
            candidates = trucks,
        )

        assertEquals(100, plan.size)
        assertEquals(99, plan.count { it.kind == RadarGeofenceKind.TRUCK })
        assertEquals(1, plan.count { it.kind == RadarGeofenceKind.SENTINEL })
        assertEquals(100, plan.map { it.requestId }.toSet().size)
        assertTrue(plan.single { it.kind == RadarGeofenceKind.SENTINEL }.notifyOnExit)
    }

    @Test
    fun `rejects invalid coordinates and unsafe radii`() {
        val candidate = RadarGeofenceSpec.truck(
            id = "truck",
            latitude = 95.0,
            longitude = 4.0,
            radiusMeters = 1f,
            distanceMeters = 1.0,
        )

        val plan = RadarGeofencePlan.create(50.0, 4.0, 1_500f, listOf(candidate))

        assertEquals(1, plan.size)
        assertEquals(RadarGeofenceKind.SENTINEL, plan.single().kind)
    }
}
