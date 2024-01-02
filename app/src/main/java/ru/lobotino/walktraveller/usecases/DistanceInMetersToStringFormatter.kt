package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.usecases.interfaces.IDistanceToStringFormatter

class DistanceInMetersToStringFormatter(
    private val metersShort: String,
    private val kilometersFull: String,
    private val kilometersShort: String
) : IDistanceToStringFormatter {

    override fun formatDistance(distance: Float): String {
        return if (distance >= 1000) {
            val kilometers: Int = (distance / 1000).toInt()
            var meters: Int = (distance % 1000).toInt()

            if (meters == 0) {
                "$kilometers $kilometersFull"
            } else {
                meters /= 10
                if (meters % 10 == 0) {
                    meters /= 10
                }
                "$kilometers.$meters $kilometersShort"
            }
        } else {
            "${distance.toInt()} $metersShort"
        }
    }
}
