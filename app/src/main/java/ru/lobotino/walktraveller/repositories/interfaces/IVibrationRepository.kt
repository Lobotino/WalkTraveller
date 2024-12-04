package ru.lobotino.walktraveller.repositories.interfaces


interface IVibrationRepository {
    fun vibrate(durationInMillis: Long, amplitude: Int)

    fun vibrateDouble(durationInMillis: Long, amplitude: Int)

    fun vibrateTriple(durationInMillis: Long, amplitude: Int)
}