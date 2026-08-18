package com.foodtruckgalaxy.ftg_radar_native

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
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
        private const val CHANNEL_ID = "ftg_radar_location"
        private const val NOTIFICATION_ID = 22107
        private const val MIN_TIME_MS = 5_000L
        private const val MIN_DISTANCE_M = 10f
    }

    private lateinit var locationManager: LocationManager
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val requestInFlight = AtomicBoolean(false)

    @Volatile private var token: String = ""
    @Volatile private var endpoint: String = ""
    private var lastSentLocation: Location? = null
    private var lastSentAtMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        intent?.getStringExtra(EXTRA_TOKEN)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            token = it
            prefs.edit().putString(KEY_TOKEN, it).apply()
        }
        intent?.getStringExtra(EXTRA_ENDPOINT)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            endpoint = it
            prefs.edit().putString(KEY_ENDPOINT, it).apply()
        }

        if (token.isEmpty()) token = prefs.getString(KEY_TOKEN, "").orEmpty()
        if (endpoint.isEmpty()) endpoint = prefs.getString(KEY_ENDPOINT, "").orEmpty()

        // Android impose startForeground très rapidement après startForegroundService.
        startForeground(NOTIFICATION_ID, buildNotification())

        if (token.isEmpty() || endpoint.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        try {
            locationManager.removeUpdates(this)
        } catch (_: Throwable) {}

        val looper = Looper.getMainLooper()

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_MS,
                    MIN_DISTANCE_M,
                    this,
                    looper
                )
            }
        } catch (_: Throwable) {}

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    MIN_TIME_MS,
                    MIN_DISTANCE_M,
                    this,
                    looper
                )
            }
        } catch (_: Throwable) {}

        // Premier point si Android en possède déjà un récent.
        bestLastKnownLocation()?.let { location ->
            if (System.currentTimeMillis() - location.time <= 120_000L) {
                handleLocation(location, force = true)
            }
        }
    }

    private fun bestLastKnownLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val locations = mutableListOf<Location>()
        try { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(locations::add) } catch (_: Throwable) {}
        try { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let(locations::add) } catch (_: Throwable) {}
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

        // On privilégie une position exploitable mais on ne bloque pas le NETWORK_PROVIDER.
        if (!force && location.hasAccuracy() && location.accuracy > 250f) return

        lastSentLocation = Location(location)
        lastSentAtMs = now
        syncLocation(location)
    }

    private fun syncLocation(location: Location) {
        if (requestInFlight.getAndSet(true)) return

        val currentToken = token
        val currentEndpoint = endpoint

        networkExecutor.execute {
            try {
                val body = JSONObject().apply {
                    put("token", currentToken)
                    put("lat", location.latitude)
                    put("lng", location.longitude)
                    if (location.hasAccuracy()) put("accuracy", location.accuracy.toDouble())
                    put("captured_at", Instant.ofEpochMilli(location.time).toString())
                }

                val connection = (URL(currentEndpoint).openConnection() as HttpURLConnection).apply {
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
                connection.disconnect()

                // Token révoqué / compte changé : on coupe immédiatement ce service.
                if (code == 401 || code == 403) {
                    stopSelf()
                }
            } catch (_: Throwable) {
                // Une coupure réseau temporaire ne doit jamais tuer le Radar.
            } finally {
                requestInFlight.set(false)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radar Food Truck Galaxy",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Localisation nécessaire au Radar FTG"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
        // Ne PAS arrêter le service lorsque l'utilisateur retire l'UI des apps récentes.
        // START_STICKY permet aussi à Android de recréer le service après une mort mémoire.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try { locationManager.removeUpdates(this) } catch (_: Throwable) {}
        networkExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
