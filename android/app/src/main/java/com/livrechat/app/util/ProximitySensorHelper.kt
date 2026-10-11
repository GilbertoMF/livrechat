package com.livrechat.app.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.util.Log

class ProximitySensorHelper(private val context: Context) : SensorEventListener {
    private val TAG = "ProximitySensorHelper"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private var isListening = false
    var onProximityChanged: ((isNear: Boolean) -> Unit)? = null
    var isNearEar: Boolean = false
        private set

    init {
        try {
            if (powerManager != null && powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "LivreChat:ProximityScreenOff"
                ).apply {
                    setReferenceCounted(false)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock não suportado neste aparelho: ${e.message}")
        }
    }

    fun startListening() {
        if (isListening || proximitySensor == null) return
        isListening = true
        sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        sensorManager?.unregisterListener(this)
        releaseWakeLock()
        isNearEar = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_PROXIMITY) return
        val distance = event.values[0]
        val maxRange = proximitySensor?.maximumRange ?: 5f
        val near = distance < maxRange.coerceAtMost(5f)
        if (near != isNearEar) {
            isNearEar = near
            if (near) {
                acquireWakeLock()
            } else {
                releaseWakeLock()
            }
            onProximityChanged?.invoke(near)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(15 * 60 * 1000L) // 15 min max
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao adquirir wakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
            }
        } catch (e: Exception) {
            try { wakeLock?.release() } catch (ex: Exception) {}
        }
    }
}
