package ru.lobotino.walktraveller.usecases

import android.content.Context
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.usecases.interfaces.IDistanceToStringFormatter

class DistanceInMetersToStringFormatter(context: Context) : IDistanceToStringFormatter {

    private val metersFull = context.getString(R.string.meters_full)
    private val kilometersFull = context.getString(R.string.kilometers_full)
    private val kilometersShort = context.getString(R.string.kilometers_short)

    override fun formatDistance(distance: Float): String {
        return if (distance >= 1000) {
            val kilometers: Int = (distance / 1000).toInt()
            var meters: Int = (distance % 1000).toInt()

            if (meters == 0) {
                "$kilometers $kilometersFull"
            } else {
                meters /= 10
                "$kilometers.$meters $kilometersShort"
            }
        } else {
            "${distance.toInt()} $metersFull"
        }
    }
}