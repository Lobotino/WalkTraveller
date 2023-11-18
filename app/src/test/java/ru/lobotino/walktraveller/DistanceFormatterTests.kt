package ru.lobotino.walktraveller

import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.usecases.DistanceInMetersToStringFormatter
import ru.lobotino.walktraveller.usecases.interfaces.IDistanceToStringFormatter

class DistanceFormatterTests {

    companion object {
        private const val metersFull = "m"
        private const val kilometersFull = "Kilometers"
        private const val kilometersShort = "Km"
    }

    private lateinit var distanceFormatter: IDistanceToStringFormatter

    @Before
    fun prepare() {
        distanceFormatter =
            DistanceInMetersToStringFormatter(metersFull, kilometersFull, kilometersShort)
    }

    @Test
    fun format0Test() {
        assert(distanceFormatter.formatDistance(0f) == "0 $metersFull")
    }

    @Test
    fun format100Test() {
        assert(distanceFormatter.formatDistance(100f) == "100 $metersFull")
    }

    @Test
    fun format1000Test() {
        assert(distanceFormatter.formatDistance(1000f) == "1 $kilometersFull")
    }

    @Test
    fun format1100Test() {
        assert(distanceFormatter.formatDistance(1100f) == "1.1 $kilometersShort")
    }

    @Test
    fun format1230Test() {
        assert(distanceFormatter.formatDistance(1230f) == "1.23 $kilometersShort")
    }

    @Test
    fun format1234Test() {
        assert(distanceFormatter.formatDistance(1234f) == "1.23 $kilometersShort")
    }
}