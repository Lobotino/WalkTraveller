package ru.lobotino.walktraveller.usecases.interfaces

import ru.lobotino.walktraveller.model.TileSource
import ru.lobotino.walktraveller.model.TileSourceType

interface ITileSourceInteractor {
    fun getCurrentTileSource(): TileSource
    fun getCurrentTileSourceType(): TileSourceType
    fun setCurrentTileSourceType(tileSourceType: TileSourceType)
}