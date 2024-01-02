package ru.lobotino.walktraveller.repositories

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import ru.lobotino.walktraveller.repositories.interfaces.IUserRotationRepository
import kotlin.math.abs
import kotlin.math.round

class UserRotationRepository(
    private val sensorManager: SensorManager,
    private val coroutineScope: CoroutineScope
) : IUserRotationRepository {

    companion object {
        private const val UPDATE_ROTATION_DELAY = 200L
        private const val MIN_ANGLE_DIFFERENCE = 5
    }

    private var updateRotationNow = false
    private var updateRotationJob: Job? = null
    private var calculateRotationJob: Job = Job()

    private val userRotationFlow = MutableSharedFlow<Float>(1, 0, BufferOverflow.DROP_OLDEST)

    private val sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val sensorMagneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var deviceAccelerometerValues: FloatArray? = null
    private var deviceMagneticFieldValues: FloatArray? = null

    private var currentRotationAngle = 0f

    private val sensorAccelerometerListener: SensorEventListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent) {
            deviceAccelerometerValues = event.values
            tryCalculateUserRotation()
        }
    }

    private val sensorMagneticFieldListener: SensorEventListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent) {
            deviceMagneticFieldValues = event.values
            tryCalculateUserRotation()
        }
    }

    private fun tryCalculateUserRotation() {
        if (deviceAccelerometerValues != null && deviceMagneticFieldValues != null) {
            calculateUserRotation(deviceAccelerometerValues!!, deviceMagneticFieldValues!!)
        }
    }

    private fun calculateUserRotation(
        accelerometerValues: FloatArray,
        geomagneticValues: FloatArray
    ) {
        coroutineScope.plus(Dispatchers.Default + calculateRotationJob).launch {
            val rotationMatrix = FloatArray(9)
            if (SensorManager.getRotationMatrix(
                    rotationMatrix,
                    null,
                    accelerometerValues,
                    geomagneticValues
                )
            ) {
                val newAngle = SensorManager.getOrientation(
                    rotationMatrix,
                    FloatArray(3)
                )[0] * 45

                if (abs(newAngle - currentRotationAngle) > MIN_ANGLE_DIFFERENCE) {
                    currentRotationAngle = newAngle
                }
            } else {
                deviceAccelerometerValues = null
                deviceMagneticFieldValues = null
            }
        }
    }

    override fun startTrackUserRotation() {
        updateRotationNow = true

        calculateRotationJob.cancel()
        calculateRotationJob = Job()

        updateRotationJob?.cancel()
        updateRotationJob = coroutineScope.launch {
            while (updateRotationNow) {
                delay(UPDATE_ROTATION_DELAY)
                if (updateRotationNow) {
                    userRotationFlow.tryEmit(round(currentRotationAngle))
                }
            }
        }

        sensorManager.registerListener(
            sensorAccelerometerListener,
            sensorAccelerometer,
            Sensor.TYPE_ACCELEROMETER
        )

        sensorManager.registerListener(
            sensorMagneticFieldListener,
            sensorMagneticField,
            Sensor.TYPE_MAGNETIC_FIELD
        )
    }

    override fun stopTrackUserRotation() {
        updateRotationNow = false
        updateRotationJob?.cancel()
        calculateRotationJob.cancel()
        sensorManager.unregisterListener(sensorAccelerometerListener)
        sensorManager.unregisterListener(sensorMagneticFieldListener)
    }

    override fun observeUserRotation(): Flow<Float> {
        return userRotationFlow
    }
}
