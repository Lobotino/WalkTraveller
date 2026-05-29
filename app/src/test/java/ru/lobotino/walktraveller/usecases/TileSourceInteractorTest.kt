package ru.lobotino.walktraveller.usecases

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.model.TileSource
import ru.lobotino.walktraveller.model.TileSourceType
import ru.lobotino.walktraveller.repositories.interfaces.ITileSourceRepository

class TileSourceInteractorTest {

    private lateinit var tileSourceRepository: ITileSourceRepository
    private lateinit var sut: TileSourceInteractor

    @Before
    fun setUp() {
        tileSourceRepository = mockk()
        sut = TileSourceInteractor(tileSourceRepository)
    }

    @Test
    fun `getCurrentTileSourceType delegates to repository`() {
        // given
        every { tileSourceRepository.getCurrentTileSourceType() } returns TileSourceType.POSITRON

        // when
        val result = sut.getCurrentTileSourceType()

        // then
        assertEquals(TileSourceType.POSITRON, result)
    }

    @Test
    fun `setCurrentTileSourceType delegates to repository`() {
        // given
        every { tileSourceRepository.setCurrentTileSourceType(any()) } just Runs

        // when
        sut.setCurrentTileSourceType(TileSourceType.LIBERTY)

        // then
        verify(exactly = 1) { tileSourceRepository.setCurrentTileSourceType(TileSourceType.LIBERTY) }
    }

    @Test
    fun `getCurrentTileSource maps LIBERTY type to liberty style url`() {
        // given
        every { tileSourceRepository.getCurrentTileSourceType() } returns TileSourceType.LIBERTY

        // when
        val result = sut.getCurrentTileSource()

        // then
        assertEquals(TileSource("https://tiles.openfreemap.org/styles/liberty"), result)
    }

    @Test
    fun `getCurrentTileSource maps POSITRON type to positron style url`() {
        // given
        every { tileSourceRepository.getCurrentTileSourceType() } returns TileSourceType.POSITRON

        // when
        val result = sut.getCurrentTileSource()

        // then
        assertEquals(TileSource("https://tiles.openfreemap.org/styles/positron"), result)
    }
}
