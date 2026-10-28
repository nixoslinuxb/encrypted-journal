package com.encryptedjournal.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

object LocationUtils {

    @SuppressLint("MissingPermission")
    fun startRealTimeLocationUpdates(
        context: Context,
        scope: CoroutineScope,
        onLocationFetched: (placeName: String, lat: Double, lng: Double) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            onError("Location permission required")
            return {}
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            onError("Location services unavailable")
            return {}
        }

        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            onError("Please turn on GPS/Location in system settings")
            return {}
        }

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                scope.launch(Dispatchers.IO) {
                    val placeName = reverseGeocode(context, location.latitude, location.longitude)
                    withContext(Dispatchers.Main) {
                        onLocationFetched(placeName, location.latitude, location.longitude)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            val lastLocation = (if (isGpsEnabled) locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null)
                ?: (if (isNetworkEnabled) locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null)
                ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

            if (lastLocation != null) {
                scope.launch(Dispatchers.IO) {
                    val placeName = reverseGeocode(context, lastLocation.latitude, lastLocation.longitude)
                    withContext(Dispatchers.Main) {
                        onLocationFetched(placeName, lastLocation.latitude, lastLocation.longitude)
                    }
                }
            }

            if (isGpsEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    1f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
        } catch (e: Exception) {
            onError("Error tracking location: ${e.localizedMessage}")
        }

        return {
            try {
                locationManager.removeUpdates(locationListener)
            } catch (_: Exception) {}
        }
    }

    private suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var asyncName = ""
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val locality = address.locality ?: address.subAdminArea ?: address.adminArea ?: address.featureName
                        val country = address.countryName
                        asyncName = when {
                            !locality.isNullOrBlank() && !country.isNullOrBlank() -> "$locality, $country"
                            !locality.isNullOrBlank() -> locality
                            else -> String.format(Locale.getDefault(), "%.4f, %.4f", latitude, longitude)
                        }
                    }
                }
                if (asyncName.isNotBlank()) return@withContext asyncName
            }

            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val locality = address.locality ?: address.subAdminArea ?: address.adminArea ?: address.featureName
                val country = address.countryName
                when {
                    !locality.isNullOrBlank() && !country.isNullOrBlank() -> "$locality, $country"
                    !locality.isNullOrBlank() -> locality
                    else -> String.format(Locale.getDefault(), "%.4f, %.4f", latitude, longitude)
                }
            } else {
                String.format(Locale.getDefault(), "%.4f, %.4f", latitude, longitude)
            }
        } catch (e: Exception) {
            String.format(Locale.getDefault(), "%.4f, %.4f", latitude, longitude)
        }
    }
}
