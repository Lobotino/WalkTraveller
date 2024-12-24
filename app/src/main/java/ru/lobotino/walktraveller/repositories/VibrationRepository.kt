package ru.lobotino.walktraveller.repositories

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import ru.lobotino.walktraveller.repositories.interfaces.IVibrationRepository

@Suppress("DEPRECATION")
class VibrationRepository(private val appContext: Context) : IVibrationRepository {

    override fun vibrate(durationInMillis: Long, amplitude: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrationEffect =
                VibrationEffect.createOneShot(durationInMillis, amplitude)
            getVibrator().vibrate(vibrationEffect)
        } else {
            getVibrator().vibrate(durationInMillis)
        }
    }

    override fun vibrateDouble(durationInMillis: Long, amplitude: Int) {
        val vibrationPattern = longArrayOf(0, durationInMillis, durationInMillis, durationInMillis)
        val vibrationAmplitudes = intArrayOf(0, amplitude, 0, amplitude)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrationEffect =
                VibrationEffect.createWaveform(vibrationPattern, vibrationAmplitudes, -1)
            getVibrator().vibrate(vibrationEffect)
        } else {
            getVibrator().vibrate(vibrationPattern, -1)
        }
    }

    override fun vibrateTriple(durationInMillis: Long, amplitude: Int) {
        val vibrationPattern =
            longArrayOf(0, durationInMillis, durationInMillis, durationInMillis, durationInMillis, durationInMillis)
        val vibrationAmplitudes = intArrayOf(0, amplitude, 0, amplitude, 0, amplitude)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrationEffect =
                VibrationEffect.createWaveform(vibrationPattern, vibrationAmplitudes, -1)
            getVibrator().vibrate(vibrationEffect)
        } else {
            getVibrator().vibrate(vibrationPattern, -1)
        }
    }

    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
}
