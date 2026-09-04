package nisargpatel.deadreckoning.adapter

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nisargpatel.deadreckoning.domain.state.SensorState
import nisargpatel.deadreckoning.imu.ImuSample
import nisargpatel.deadreckoning.imu.ImuSource
import nisargpatel.deadreckoning.imu.ImuSourceAdapter
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt

/** Android IMU adapter with rotation-vector attitude, stationary gyro-bias learning, and mount-change checks. */
class SensorAdapter(context: Context) : SensorEventListener, ImuSourceAdapter {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /**
     * Platform gravity estimate, needed by the IDR-V1 contract.
     *
     * That contract declares gravity REMOVED, and the model was trained on IO-VNBD's own
     * GRAVITY column rather than on a locally filtered approximation. TYPE_GRAVITY is the
     * closest equivalent Android offers, so using it keeps training and runtime aligned.
     */
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val _sensorState = MutableStateFlow(SensorState(
        isAccelAvailable = accelSensor != null, isGyroAvailable = gyroSensor != null, isMagAvailable = magSensor != null
    ))
    val sensorState: StateFlow<SensorState> = _sensorState.asStateFlow()
    var onImuSample: ((ImuSample) -> Unit)? = null
    override val source = ImuSource.PHONE

    /**
     * Latest device-to-ENU rotation matrix, row-major 3x3.
     *
     * Held outside [SensorState] because a FloatArray in a data class breaks
     * structural equality and would defeat StateFlow de-duplication. Stage 1 uses
     * this to rotate IMU vectors into the vehicle frame; nothing consumed device
     * attitude as a matrix before.
     */
    @Volatile
    var rotationMatrix: FloatArray? = null
        private set

    /**
     * Timestamp of the most recent IMU sample, on the sensor event clock.
     *
     * Exposed so downstream integration measures intervals against the same timebase as the
     * samples themselves. Mixing this with `System.nanoTime()` produces integration
     * intervals that do not correspond to the data being integrated.
     */
    @Volatile
    var latestSampleTimestampNs: Long = 0L
        private set

    /**
     * Latest platform gravity vector in device axes, or null when unavailable.
     *
     * Kept outside [SensorState] for the same reason as the rotation matrix: a FloatArray
     * breaks data-class equality and would defeat StateFlow de-duplication.
     */
    @Volatile
    var gravityVector: FloatArray? = null
        private set

    private var gravityValues = FloatArray(3)
    private var magValues = FloatArray(3)
    private val accelerometerTimestamps = ArrayDeque<Long>()
    private val stationaryGyros = ArrayDeque<FloatArray>()
    private var gyroBias = FloatArray(3)
    private var stationarySinceNs = 0L
    private var referenceMount: FloatArray? = null
    private var mountChanged = false

    fun startListening() {
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stopListening() = sensorManager.unregisterListener(this)
    override fun start(onSample: (ImuSample) -> Unit) { onImuSample = onSample; startListening() }
    override fun stop() { onImuSample = null; stopListening() }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
            Sensor.TYPE_GYROSCOPE -> handleGyroscope(event)
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magValues = event.values.clone()
                _sensorState.value = _sensorState.value.copy(magX = event.values[0], magY = event.values[1], magZ = event.values[2])
                if (rotationSensor == null) updateMagneticOrientation()
            }
            Sensor.TYPE_ROTATION_VECTOR -> updateRotationVectorOrientation(event.values)
            Sensor.TYPE_GRAVITY -> gravityVector = event.values.clone()
        }
    }

    private fun handleAccelerometer(event: SensorEvent) {
        gravityValues = event.values.clone()
        val ax = event.values[0]; val ay = event.values[1]; val az = event.values[2]
        val magnitude = sqrt(ax * ax + ay * ay + az * az)
        val samplingHz = updateSamplingRate(event.timestamp)
        val stableGravity = abs(magnitude - 9.81f) < 0.18f
        val stationary = stableGravity && gyroMagnitude(_sensorState.value) < 0.08f
        if (stationary && stationarySinceNs == 0L) stationarySinceNs = event.timestamp
        if (!stationary) stationarySinceNs = 0L
        val mountStability = (100f - abs(magnitude - 9.81f) * 12f).toInt().coerceIn(0, 100)
        _sensorState.value = _sensorState.value.copy(
            accelX = ax, accelY = ay, accelZ = az, accelMagnitude = magnitude, imuSamplingHz = samplingHz,
            mountStabilityPercentage = mountStability, isStationary = stationary,
            alignmentConfidencePercentage = if (rotationSensor != null || magSensor != null) mountStability else 0,
            overallHealthPercentage = listOf(accelSensor, gyroSensor, magSensor).count { it != null } * 100 / 3
        )
        if (rotationSensor == null) updateMagneticOrientation()
        observeMountWhenStable(event.timestamp)
    }

    private fun handleGyroscope(event: SensorEvent) {
        val raw = event.values
        if (_sensorState.value.isStationary) updateGyroBias(raw)
        val corrected = FloatArray(3) { index -> raw[index] - gyroBias[index] }
        latestSampleTimestampNs = event.timestamp
        val updated = _sensorState.value.copy(
            gyroX = corrected[0], gyroY = corrected[1], gyroZ = corrected[2],
            gyroBiasX = gyroBias[0], gyroBiasY = gyroBias[1], gyroBiasZ = gyroBias[2], isMountChanged = mountChanged
        )
        _sensorState.value = updated
        onImuSample?.invoke(ImuSample(event.timestamp, updated.accelX, updated.accelY, updated.accelZ, updated.gyroX, updated.gyroY, updated.gyroZ, updated.magX, updated.magY, updated.magZ, ImuSource.PHONE))
    }

    private fun updateGyroBias(raw: FloatArray) {
        stationaryGyros += raw.clone()
        while (stationaryGyros.size > 150) stationaryGyros.removeFirst()
        if (stationaryGyros.size < 30) return
        gyroBias = FloatArray(3) { axis -> stationaryGyros.map { it[axis] }.average().toFloat() }
    }

    private fun updateRotationVectorOrientation(values: FloatArray) {
        val matrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(matrix, values)
        rotationMatrix = matrix
        updateOrientation(matrix, true)
    }

    private fun updateMagneticOrientation() {
        val matrix = FloatArray(9)
        if (SensorManager.getRotationMatrix(matrix, null, gravityValues, magValues)) {
            rotationMatrix = matrix
            updateOrientation(matrix, false)
        }
    }

    private fun updateOrientation(rotationMatrix: FloatArray, usesRotationVector: Boolean) {
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        _sensorState.value = _sensorState.value.copy(
            yawDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat(),
            pitchDegrees = Math.toDegrees(orientation[1].toDouble()).toFloat(),
            rollDegrees = Math.toDegrees(orientation[2].toDouble()).toFloat(),
            usesRotationVector = usesRotationVector
        )
    }

    private fun observeMountWhenStable(timestampNs: Long) {
        if (stationarySinceNs == 0L || timestampNs - stationarySinceNs < 3_000_000_000L) return
        val state = _sensorState.value
        val orientation = floatArrayOf(state.pitchDegrees, state.rollDegrees, state.yawDegrees)
        val reference = referenceMount
        if (reference == null) referenceMount = orientation
        else if (angularDifference(reference[0], orientation[0]) > 12f || angularDifference(reference[1], orientation[1]) > 12f || angularDifference(reference[2], orientation[2]) > 20f) mountChanged = true
    }

    private fun gyroMagnitude(state: SensorState) = sqrt(state.gyroX * state.gyroX + state.gyroY * state.gyroY + state.gyroZ * state.gyroZ)
    private fun angularDifference(first: Float, second: Float): Float = abs(((first - second + 540f) % 360f) - 180f)
    private fun updateSamplingRate(timestampNs: Long): Int {
        accelerometerTimestamps += timestampNs
        val cutoff = timestampNs - 1_000_000_000L
        while (accelerometerTimestamps.isNotEmpty() && accelerometerTimestamps.first() < cutoff) accelerometerTimestamps.removeFirst()
        return accelerometerTimestamps.size
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
