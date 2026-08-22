package com.foodtruckgalaxy.ftg_radar_native

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RadarConfigTest {
    @Test
    fun `normalizes a valid configuration`() {
        val config = RadarConfig.from(
            token = "  radar-token  ",
            endpoint = "  https://api.foodtruckgalaxy.be/radar/position  ",
        )

        assertEquals("radar-token", config.token)
        assertEquals(
            "https://api.foodtruckgalaxy.be/radar/position",
            config.endpoint,
        )
    }

    @Test
    fun `rejects blank token`() {
        assertThrows(IllegalArgumentException::class.java) {
            RadarConfig.from("   ", "https://api.foodtruckgalaxy.be/radar")
        }
    }

    @Test
    fun `rejects non https and relative endpoints`() {
        assertThrows(IllegalArgumentException::class.java) {
            RadarConfig.from("token", "http://api.foodtruckgalaxy.be/radar")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RadarConfig.from("token", "/radar")
        }
    }

    @Test
    fun `formats timestamps without java time APIs`() {
        assertEquals("1970-01-01T00:00:00.000Z", RadarTimestamp.format(0L))
        assertEquals(
            "2023-11-14T22:13:20.123Z",
            RadarTimestamp.format(1_700_000_000_123L),
        )
    }
}
