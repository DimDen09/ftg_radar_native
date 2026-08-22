package com.foodtruckgalaxy.ftg_radar_native

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class RadarLocationService : Service(), LocationListener {
    companion object {
        const val ACTION_START = "com.foodtruckgalaxy.radar.START"
        const val EXTRA_TOKEN = "radar_token"
        const val EXTRA_ENDPOINT = "radar_endpoint"
        const val PREFS_NAME = "ftg_radar_native_v23"

        private const val KEY_TOKEN = "token"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_STATE = "state"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_HTTP_STATUS = "last_http_status"
        private const val KEY_LAST_ERROR = "last_error"
        private const val CHANNEL_ID = "ftg_radar_location"
        private const val NOTIFICATION_ID = 22107
        private const val MIN_TIME_MS = 5_000L
        private const val MIN_DISTANCE_M = 10f

        @Volatile
        private var active = false

        fun isRunning(): Boolean = active

        fun markStarting(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATE, "starting")
                .remove(KEY_LAST_ERROR)
                .apply()
        }

        fun recordStartFailure(context: Context, exception: RuntimeException) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATE, "start_failed")
                .putString(KEY_LAST_ERROR, safeError(exception))
                .apply()
        }

        fun clearConfiguration(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TOKEN)
                .remove(KEY_ENDPOINT)
                .putString(KEY_STATE, "stopping")
                .apply()
        }

        fun status(context: Context): Map<String, Any?> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return linkedMapOf(
                "running" to active,
                "state" to prefs.getString(KEY_STATE, if (active) "running" else "stopped"),
                "lastSyncAt" to prefs.getString(KEY_LAST_SYNC_AT, null),
                "lastHttpStatus" to if (prefs.contains(KEY_LAST_HTTP_STATUS)) {
                    prefs.getInt(KEY_LAST_HTTP_STATUS, 0)
                } else {
                    null
                },
                "lastError" to prefs.getString(KEY_LAST_ERROR, null),
            )
        }

        private fun safeError(exception: Throwable): String {
            val detail = exception.message?.take(160)?.let { ": $it" }.orEmpty()
            return "${exception.javaClass.simpleName}$detail"
        }
    }

    private lateinit var locationManager: LocationManager
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val requestInFlight = AtomicBoolean(false)

    @Volatile
    private var token = ""

    @Volatile
    private var endpoint = ""

    private var lastSentLocation: Location? = null
    private var lastSentAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        active = true
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForegroundService()

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val config = try {
            RadarConfig.from(
                intent?.getStringExtra(EXTRA_TOKEN) ?: prefs.getString(KEY_TOKEN, null),
                intent?.getStringExtra(EXTRA_ENDPOINT) ?: prefs.getString(KEY_ENDPOINT, null),
            )
        } catch (exception: IllegalArgumentException) {
            recordFailure("invalid_configuration", exception)
            clearConfiguration(this)
            stopSelf()
            return START_NOT_STICKY
        }

        token = config.token
        endpoint = config.endpoint
        prefs.edit()
            .putString(KEY_TOKEN, config.token)
            .putString(KEY_ENDPOINT, config.endpoint)
            .putString(KEY_STATE, "running")
            .remove(KEY_LAST_ERROR)
            .apply()

        startLocationUpdates()
        return START_STICKY
    }

    private fun startAsForegroundService() {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            serviceType,
        )
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            recordFailure("location_permission_missing")
            stopSelf()
            return
        }

        runCatching { locationManager.removeUpdates(this) }
        val looper = Looper.getMainLooper()
        var registeredProviders = 0

        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    MIN_TIME_MS,
                    MIN_DISTANCE_M,
                    this,
                    looper,
                )
                registeredProviders += 1
            } catch (exception: RuntimeException) {
                recordFailure("provider_${provider}_failed", exception)
            }
        }

        if (registeredProviders == 0) {
            stopSelf()
            return
        }

        bestLastKnownLocation()?.let { location ->
            if (System.currentTimeMillis() - location.time <= 120_000L) {
                handleLocation(location, force = true)
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    private fun bestLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null

        val locations = mutableListOf<Location>()
        try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(locations::add)
        } catch (exception: RuntimeException) {
            recordFailure("last_gps_location_failed", exception)
        }
        try {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let(locations::add)
        } catch (exception: RuntimeException) {
            recordFailure("last_network_location_failed", exception)
        }
        return locations.maxByOrNull { it.time }
    }

    override fun onLocationChanged(location: Location) {
        handleLocation(location, force = false)
    }

    private fun handleLocation(location: Location, force: Boolean) {
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return

        val now = System.currentTimeMillis()
        if (!force && now - lastSentAtMs < 4_000L) return

        val previous = lastSentLocation
        if (!force && previous != null && previous.distanceTo(location) < MIN_DISTANCE_M) return
        if (!force && location.hasAccuracy() && location.accuracy > 250f) return

        lastSentLocation = Location(location)
        lastSentAtMs = now
        syncLocation(location)
    }

    private fun syncLocation(location: Location) {
        if (!requestInFlight.compareAndSet(false, true)) return

        val currentToken = token
        val currentEndpoint = endpoint
        networkExecutor.execute {
            var connection: HttpURLConnection? = null
            try {
                val body = JSONObject().apply {
                    put("token", currentToken)
                    put("lat", location.latitude)
                    put("lng", location.longitude)
                    if (location.hasAccuracy()) put("accuracy", location.accuracy.toDouble())
                    put("captured_at", RadarTimestamp.format(location.time))
                }

                connection = (URL(currentEndpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-FTG-Radar", "native-v23")
                }

                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                recordHttpResult(code)
                if (code == 401 || code == 403) {
                    clearConfiguration(this)
                    recordFailure("authentication_revoked")
                    stopSelf()
                }
            } catch (exception: RuntimeException) {
                recordFailure("network_failed", exception)
            } finally {
                connection?.disconnect()
                requestInFlight.set(false)
            }
        }
    }

    private fun recordHttpResult(code: Int) {
        val editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_HTTP_STATUS, code)
        if (code in 200..299) {
            editor
                .putString(KEY_LAST_SYNC_AT, RadarTimestamp.format(System.currentTimeMillis()))
                .remove(KEY_LAST_ERROR)
        } else {
            editor.putString(KEY_LAST_ERROR, "http_status_$code")
        }
        editor.apply()
    }

    private fun recordFailure(code: String, exception: Throwable? = null) {
        val detail = exception?.let { ": ${safeError(it)}" }.orEmpty()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ERROR, "$code$detail")
            .apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radar Food Truck Galaxy",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Localisation nécessaire au Radar FTG"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("Radar FTG actif")
            .setContentText("Food Truck Galaxy surveille les food trucks compatibles autour de vous.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        active = false
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, "stopped")
            .apply()
        runCatching { locationManager.removeUpdates(this) }
        networkExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
