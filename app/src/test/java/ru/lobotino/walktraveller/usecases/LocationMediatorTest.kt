package ru.lobotino.walktraveller.usecases

import android.location.Location
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LocationMediatorTest {

    @Test
    fun `first location is always reported as real`() {
        // given
        val sut = LocationMediator(lastLocation = null)
        val newLocation = mockk<Location>()
        var reported: Location? = null

        // when
        sut.onNewLocation(newLocation) { reported = it }

        // then
        assertSame(newLocation, reported)
    }

    @Test
    fun `location within realistic distance is reported`() {
        // given
        val lastLocation = mockk<Location>()
        val newLocation = mockk<Location>()
        every { lastLocation.distanceTo(newLocation) } returns 5f
        val sut = LocationMediator(lastLocation = lastLocation)
        var reported: Location? = null

        // when
        sut.onNewLocation(newLocation) { reported = it }

        // then
        assertSame(newLocation, reported)
    }

    @Test
    fun `location beyond realistic distance is treated as fake and ignored`() {
        // given
        val lastLocation = mockk<Location>()
        val newLocation = mockk<Location>()
        every { lastLocation.distanceTo(newLocation) } returns 10_000_001_000f
        every { lastLocation.latitude } returns 1.0
        every { lastLocation.longitude } returns 2.0
        every { newLocation.latitude } returns 80.0
        every { newLocation.longitude } returns 90.0
        val sut = LocationMediator(lastLocation = lastLocation)
        var reported: Location? = null

        // when
        sut.onNewLocation(newLocation) { reported = it }

        // then
        assertNull(reported)
    }
}
