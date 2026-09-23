package com.mewname.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.round

/** One foreground, approximate fix. No service or background location tracking. */
internal object WeatherDeviceLocation {
    fun permitted(context: Context) = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    @android.annotation.SuppressLint("MissingPermission")
    suspend fun coordinates(context: Context): Pair<Double, Double>? {
        if (!permitted(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, "fused")
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val cached = providers.mapNotNull {
            runCatching { manager.getLastKnownLocation(it) }.getOrNull()
        }.filter { usable(it) }.maxByOrNull { it.elapsedRealtimeNanos }
        if (cached != null) return rounded(cached)
        for (provider in providers) {
            val location = try {
                withTimeoutOrNull(8_000) {
                    suspendCancellableCoroutine<Location?> { continuation ->
                        val cancellation = CancellationSignal()
                        continuation.invokeOnCancellation { cancellation.cancel() }
                        try {
                            LocationManagerCompat.getCurrentLocation(manager, provider, cancellation,
                                ContextCompat.getMainExecutor(context)) { value ->
                                if (continuation.isActive) continuation.resume(value)
                            }
                        } catch (_: Exception) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null }
            if (location != null && usable(location)) return rounded(location)
        }
        return null
    }

    private fun usable(location: Location): Boolean {
        val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        return age in 0..600_000_000_000L &&
            location.latitude.isFinite() && location.longitude.isFinite() &&
            location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0 &&
            location.hasAccuracy() && location.accuracy in 0f..20_000f
    }

    private fun rounded(location: Location) =
        round(location.latitude * 100) / 100 to round(location.longitude * 100) / 100
}