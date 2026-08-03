package com.example.nanohead.sensors

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TelemetryState(
    val ramAvailableMb: Long = 0,
    val ramTotalMb: Long = 0,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f
)

class HardwareTelemetry(private val context: Context) : SensorEventListener {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _telemetryState = MutableStateFlow(TelemetryState())
    val telemetryState: StateFlow<TelemetryState> = _telemetryState

    fun start() {
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun updateMemoryStats() {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val availMb = memoryInfo.availMem / (1024 * 1024)
        val totalMb = memoryInfo.totalMem / (1024 * 1024)
        
        _telemetryState.value = _telemetryState.value.copy(
            ramAvailableMb = availMb,
            ramTotalMb = totalMb
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                _telemetryState.value = _telemetryState.value.copy(
                    accelX = event.values[0],
                    accelY = event.values[1],
                    accelZ = event.values[2]
                )
            }
            Sensor.TYPE_GYROSCOPE -> {
                _telemetryState.value = _telemetryState.value.copy(
                    gyroX = event.values[0],
                    gyroY = event.values[1],
                    gyroZ = event.values[2]
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
