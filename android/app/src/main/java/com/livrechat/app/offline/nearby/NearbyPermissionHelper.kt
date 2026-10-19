package com.livrechat.app.offline.nearby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utilitário sênior para validação e requisição de permissões de proximidade (Android 12/13/14+)
 */
object NearbyPermissionHelper {

    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31 & 32
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else { // Android 11 ou inferior
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        return permissions.toTypedArray()
    }

    fun hasNearbyPermissions(context: Context): Boolean {
        val required = getRequiredPermissions()
        return required.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            adapter != null && adapter.isEnabled
        } catch (e: Exception) {
            false
        }
    }

    fun isLocationEnabled(context: Context): Boolean {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (locationManager != null) {
                androidx.core.location.LocationManagerCompat.isLocationEnabled(locationManager)
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun isWifiEnabled(context: Context): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            wifiManager?.isWifiEnabled ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun isHardwareReady(context: Context): Boolean {
        return hasNearbyPermissions(context) && isBluetoothEnabled() && isLocationEnabled(context)
    }
}
