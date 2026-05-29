package ru.lobotino.walktraveller.usecases

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.LastSeenPointRepository

class MapStateInteractorTest {

    private lateinit var lastSeenPointRepository: LastSeenPointRepository
    private lateinit var sut: MapStateInteractor

    @Before
    fun setUp() {
        lastSeenPointRepository = mockk()
        sut = MapStateInteractor(lastSeenPointRepository)
    }

    @Test
    fun `getLastSeenPoint returns stored point when present`() {
        // given
        val storedPoint = MapPoint(10.0, 20.0)
        every { lastSeenPointRepository.getLastSeenPoint() } returns storedPoint

        // when
        val result = sut.getLastSeenPoint()

        // then
        assertEquals(storedPoint, result)
    }

    @Test
    fun `getLastSeenPoint falls back to Moscow when nothing stored`() {
        // given
        every { lastSeenPointRepository.getLastSeenPoint() } returns null

        // when
        val result = sut.getLastSeenPoint()

        // then
        assertEquals(MapPoint(55.7522200, 37.6155600), result)
    }

    @Test
    fun `setLastSeenPoint delegates to repository`() {
        // given
        val point = MapPoint(1.0, 2.0)
        every { lastSeenPointRepository.setLastSeenPoint(any()) } just Runs

        // when
        sut.setLastSeenPoint(point)

        // then
        verify(exactly = 1) { lastSeenPointRepository.setLastSeenPoint(point) }
    }
}
