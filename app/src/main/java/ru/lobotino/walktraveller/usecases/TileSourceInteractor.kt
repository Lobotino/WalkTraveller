package ru.lobotino.walktraveller.usecases

import org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK
import org.osmdroid.tileprovider.tilesource.TileSourceFactory.OpenTopo
import ru.lobotino.walktraveller.model.TileSource
import ru.lobotino.walktraveller.model.TileSourceType
import ru.lobotino.walktraveller.repositories.interfaces.ITileSourceRepository
import ru.lobotino.walktraveller.usecases.interfaces.ITileSourceInteractor

class TileSourceInteractor(
    private val tileSourceRepository: ITileSourceRepository,
) : ITileSourceInteractor {

    override fun getCurrentTileSource(): TileSource {
        val currentTileSourceType = tileSourceRepository.getCurrentTileSourceType()
        return when (currentTileSourceType) {
            TileSourceType.OSM_MAPNIK -> TileSource.OSMTileSource(MAPNIK)
            TileSourceType.OSM_OPEN_TOPO -> TileSource.OSMTileSource(OpenTopo)
        }
    }

    override fun getCurrentTileSourceType(): TileSourceType {
        return tileSourceRepository.getCurrentTileSourceType()
    }

    override fun setCurrentTileSourceType(tileSourceType: TileSourceType) {
        tileSourceRepository.setCurrentTileSourceType(tileSourceType)
    }
}