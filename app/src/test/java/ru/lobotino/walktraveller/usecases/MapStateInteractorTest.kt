package ru.lobotino.walktraveller.usecases

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.model.map.MapCameraState
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IMapCameraStateRepository

class MapStateInteractorTest {

    private lateinit var mapCameraStateRepository: IMapCameraStateRepository
    private lateinit var sut: MapStateInteractor

    @Before
    fun setUp() {
        mapCameraStateRepository = mockk()
        sut = MapStateInteractor(mapCameraStateRepository)
    }

    @After
    fun tearDown() {
        // mockk state is per-instance; nothing global to clear
    }

    @Test
    fun `getLastCameraState returns stored state when present`() {
        // given
        val stored = MapCameraState(MapPoint(10.0, 20.0), 17.5)
        every { mapCameraStateRepository.getLastCameraState() } returns stored

        // when
        val result = sut.getLastCameraState()

        // then
        assertEquals(stored, result)
    }

    @Test
    fun `getLastCameraState falls back to Moscow with default zoom when nothing stored`() {
        // given
        every { mapCameraStateRepository.getLastCameraState() } returns null

        // when
        val result = sut.getLastCameraState()

        // then
        assertEquals(MapCameraState(MapPoint(55.7522200, 37.6155600), 15.0), result)
    }

    @Test
    fun `setLastCameraState delegates to repository`() {
        // given
        val state = MapCameraState(MapPoint(1.0, 2.0), 14.0)
        every { mapCameraStateRepository.setLastCameraState(any()) } just Runs

        // when
        sut.setLastCameraState(state)

        // then
        verify(exactly = 1) { mapCameraStateRepository.setLastCameraState(state) }
    }
}
