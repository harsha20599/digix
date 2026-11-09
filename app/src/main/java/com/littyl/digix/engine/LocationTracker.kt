package com.littyl.digix.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Uses Android's own location service, so it works on phones without Google Play services. */
class LocationTracker(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)

    @Volatile
    var latest: Location? = null
        private set

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val current = latest
            if (current == null || isBetter(location, current)) latest = location
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    /** Starts following location, and adopts the best position the phone already knows. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasPermission(appContext)) return
        for (provider in PROVIDERS) {
            runCatching {
                manager.getLastKnownLocation(provider)?.let { known -> listener.onLocationChanged(known) }
                manager.requestLocationUpdates(provider, UPDATE_MS, 0f, listener, Looper.getMainLooper())
            }
        }
    }

    fun stop() {
        runCatching { manager.removeUpdates(listener) }
    }

    /** Waits for a position taken after this was called, or returns null if none arrives in time. */
    suspend fun awaitFresh(timeoutMs: Long): Location? {
        val startedAt = System.currentTimeMillis()
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                val current = latest
                if (current != null && current.time >= startedAt - FRESH_GRACE_MS) return@withTimeoutOrNull current
                delay(POLL_MS)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
    }

    /** A newer fix wins unless it is both barely newer and much less accurate. */
    private fun isBetter(candidate: Location, current: Location): Boolean {
        val newer = candidate.time - current.time
        if (newer > SIGNIFICANTLY_NEWER_MS) return true
        if (newer < -SIGNIFICANTLY_NEWER_MS) return false
        return candidate.accuracy <= current.accuracy || newer > 0 && candidate.accuracy <= current.accuracy + ACCURACY_SLACK_M
    }

    companion object {
        private const val UPDATE_MS = 15_000L
        private const val POLL_MS = 500L
        private const val FRESH_GRACE_MS = 5_000L
        private const val SIGNIFICANTLY_NEWER_MS = 60_000L
        private const val ACCURACY_SLACK_M = 25f
        private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused", LocationManager.PASSIVE_PROVIDER)

        fun hasPermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun isEnabled(context: Context): Boolean =
            context.getSystemService(LocationManager::class.java).isLocationEnabled

        /** The link and details for a text message. Map links work without any app on the other end. */
        fun describe(location: Location): String {
            val link = "https://maps.google.com/?q=%.6f,%.6f".format(java.util.Locale.US, location.latitude, location.longitude)
            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(location.time))
            return "$link (within ${location.accuracy.roundToInt()} m, at $time)"
        }
    }
}
