package ru.lobotino.walktraveller.usecases

import android.location.Location
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesRepository

class UserLocationInteractorTest {

    private lateinit var locationUpdatesRepository: ILocationUpdatesRepository
    private lateinit var sut: UserLocationInteractor

    @Before
    fun setUp() {
        locationUpdatesRepository = mockk()
        sut = UserLocationInteractor(locationUpdatesRepository)
    }

    @Test
    fun `onSuccess receives location converted to MapPoint`() {
        // given
        val location = mockk<Location>()
        every { location.latitude } returns 12.0
        every { location.longitude } returns 34.0
        every { locationUpdatesRepository.updateLocationNow(any(), any(), any()) } answers {
            firstArg<(Location) -> Unit>().invoke(location)
        }
        var result: MapPoint? = null

        // when
        sut.getCurrentUserLocation(onSuccess = { result = it }, onEmpty = {}, onError = {})

        // then
        assertEquals(MapPoint(12.0, 34.0), result)
    }

    @Test
    fun `onEmpty is propagated to caller`() {
        // given
        every { locationUpdatesRepository.updateLocationNow(any(), any(), any()) } answers {
            secondArg<() -> Unit>().invoke()
        }
        var emptyCalled = false

        // when
        sut.getCurrentUserLocation(onSuccess = {}, onEmpty = { emptyCalled = true }, onError = {})

        // then
        assertTrue(emptyCalled)
    }

    @Test
    fun `onError is propagated to caller`() {
        // given
        val exception = RuntimeException("location failed")
        every { locationUpdatesRepository.updateLocationNow(any(), any(), any()) } answers {
            thirdArg<(Exception) -> Unit>().invoke(exception)
        }
        var result: Exception? = null

        // when
        sut.getCurrentUserLocation(onSuccess = {}, onEmpty = {}, onError = { result = it })

        // then
        assertSame(exception, result)
    }
}
