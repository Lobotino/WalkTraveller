package ru.lobotino.walktraveller.usecases

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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
        every { tileSourceRepository.getCurrentTileSourceType() } returns TileSourceType.OSM_OPEN_TOPO

        // when
        val result = sut.getCurrentTileSourceType()

        // then
        assertEquals(TileSourceType.OSM_OPEN_TOPO, result)
    }

    @Test
    fun `setCurrentTileSourceType delegates to repository`() {
        // given
        every { tileSourceRepository.setCurrentTileSourceType(any()) } just Runs

        // when
        sut.setCurrentTileSourceType(TileSourceType.OSM_MAPNIK)

        // then
        verify(exactly = 1) { tileSourceRepository.setCurrentTileSourceType(TileSourceType.OSM_MAPNIK) }
    }

    @Test
    fun `getCurrentTileSource maps MAPNIK type to MAPNIK osm source`() {
        // given
        every { tileSourceRepository.getCurrentTileSourceType() } returns TileSourceType.OSM_MAPNIK

        // when
        val result = sut.getCurrentTileSource()

        // then
        assertTrue(result is TileSource.OSMTileSource)
        assertSame(TileSourceFactory.MAPNIK, (result as TileSource.OSMTileSource).tileSource)
    }

    @Test
    fun `getCurrentTileSource maps OPEN_TOPO type to OpenTopo osm source`() {
        // given
        every { tileSourceRepository.getCurrentTileSourceType() } returns TileSourceType.OSM_OPEN_TOPO

        // when
        val result = sut.getCurrentTileSource()

        // then
        assertTrue(result is TileSource.OSMTileSource)
        assertSame(TileSourceFactory.OpenTopo, (result as TileSource.OSMTileSource).tileSource)
    }
}
