package ru.lobotino.walktraveller.repositories.interfaces

interface IOptimizePathsSettingsRepository {

    fun setOptimizePathsApproximationDistance(approximationDistance: Float)

    fun getOptimizePathsApproximationDistance(): Float?
}
