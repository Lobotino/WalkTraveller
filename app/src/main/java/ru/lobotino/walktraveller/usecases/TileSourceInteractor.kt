package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.model.TileSource
import ru.lobotino.walktraveller.model.TileSourceType
import ru.lobotino.walktraveller.repositories.interfaces.ITileSourceRepository
import ru.lobotino.walktraveller.usecases.interfaces.ITileSourceInteractor

class TileSourceInteractor(
    private val tileSourceRepository: ITileSourceRepository,
) : ITileSourceInteractor {

    override fun getCurrentTileSource(): TileSource =
        TileSource(styleUrlFor(tileSourceRepository.getCurrentTileSourceType()))

    override fun getCurrentTileSourceType(): TileSourceType =
        tileSourceRepository.getCurrentTileSourceType()

    override fun setCurrentTileSourceType(tileSourceType: TileSourceType) {
        tileSourceRepository.setCurrentTileSourceType(tileSourceType)
    }

    private fun styleUrlFor(type: TileSourceType): String = when (type) {
        TileSourceType.LIBERTY -> "https://tiles.openfreemap.org/styles/liberty"
        TileSourceType.POSITRON -> "https://tiles.openfreemap.org/styles/positron"
    }
}
