package com.foodtruckgalaxy.ftg_radar_native

import android.content.Context
import android.content.Intent
import android.location.Location
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal object RadarLocationSync {
    private const val KEY_LAST_SENT_LAT = "last_sent_lat"
    private const val KEY_LAST_SENT_LNG = "last_sent_lng"
    private const val KEY_LAST_SENT_AT = "last_sent_at"
    private const val MIN_DISTANCE_M = 10f
    private const val MIN_TIME_MS = 4_000L
    private const val MAX_ACCURACY_M = 250f

    private val executor = Executors.newSingleThreadExecutor()
    private val requestInFlight = AtomicBoolean(false)
    private val claimLock = Any()

    fun enqueue(
        context: Context,
        location: Location,
        force: Boolean = false,
        completion: () -> Unit = {},
    ): Boolean {
        val appContext = context.applicationContext
        if (!requestInFlight.compareAndSet(false, true)) return false

        val prefs = appContext.getSharedPreferences(
            RadarLocationService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val token = prefs.getString(RadarLocationService.KEY_TOKEN, null).orEmpty()
        val endpoint = prefs.getString(RadarLocationService.KEY_ENDPOINT, null).orEmpty()
        if (token.isBlank() || endpoint.isBlank()) {
            requestInFlight.set(false)
            return false
        }
        if (!claimLocation(appContext, location, force)) {
            requestInFlight.set(false)
            return false
        }

        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                val body = JSONObject().apply {
                    put("token", token)
                    put("lat", location.latitude)
                    put("lng", location.longitude)
                    if (location.hasAccuracy()) put("accuracy", location.accuracy.toDouble())
                    put("captured_at", RadarTimestamp.format(location.time))
                }

                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-FTG-Radar", "native-pending-intent-v35")
                }

                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                RadarLocationService.recordHttpResult(appContext, code)
                if (code == 401 || code == 403) {
                    RadarLocationService.clearConfiguration(appContext)
                    RadarLocationService.recordFailure(appContext, "authentication_revoked")
                    appContext.stopService(Intent(appContext, RadarLocationService::class.java))
                }
            } catch (exception: RuntimeException) {
                RadarLocationService.recordFailure(appContext, "network_failed", exception)
            } finally {
                connection?.disconnect()
                requestInFlight.set(false)
                completion()
            }
        }
        return true
    }

    private fun claimLocation(context: Context, location: Location, force: Boolean): Boolean {
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
            return false
        }
        if (!force && location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return false

        synchronized(claimLock) {
            val prefs = context.getSharedPreferences(
                RadarLocationService.PREFS_NAME,
                Context.MODE_PRIVATE,
            )
            val now = System.currentTimeMillis()
            val previousAt = prefs.getLong(KEY_LAST_SENT_AT, 0L)
            if (!force && now - previousAt < MIN_TIME_MS) return false

            if (!force && prefs.contains(KEY_LAST_SENT_LAT) && prefs.contains(KEY_LAST_SENT_LNG)) {
                val previous = Location("ftg-radar-previous").apply {
                    latitude = java.lang.Double.longBitsToDouble(
                        prefs.getLong(KEY_LAST_SENT_LAT, 0L),
                    )
                    longitude = java.lang.Double.longBitsToDouble(
                        prefs.getLong(KEY_LAST_SENT_LNG, 0L),
                    )
                }
                if (previous.distanceTo(location) < MIN_DISTANCE_M) return false
            }

            prefs.edit()
                .putLong(KEY_LAST_SENT_LAT, java.lang.Double.doubleToRawLongBits(location.latitude))
                .putLong(KEY_LAST_SENT_LNG, java.lang.Double.doubleToRawLongBits(location.longitude))
                .putLong(KEY_LAST_SENT_AT, now)
                .apply()
            return true
        }
    }
}
