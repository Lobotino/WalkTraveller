package ru.lobotino.walktraveller.repositories

import android.content.SharedPreferences
import ru.lobotino.walktraveller.repositories.interfaces.IOptimizePathsSettingsRepository

class OptimizePathsSettingsRepository(private val sharedPreferences: SharedPreferences) :
    IOptimizePathsSettingsRepository {

    companion object {
        private const val APPROXIMATION_DISTANCE_ID = "approximation_distance"
    }

    override fun setOptimizePathsApproximationDistance(approximationDistance: Float) {
        sharedPreferences.edit().apply {
            putFloat(APPROXIMATION_DISTANCE_ID, approximationDistance)
            apply()
        }
    }

    override fun getOptimizePathsApproximationDistance(): Float? {
        val approximationDistance = sharedPreferences.getFloat(APPROXIMATION_DISTANCE_ID, -1f)
        return if (approximationDistance == -1f) null else approximationDistance
    }
}
