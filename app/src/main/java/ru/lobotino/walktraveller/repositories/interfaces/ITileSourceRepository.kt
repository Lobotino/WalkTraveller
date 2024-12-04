package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.TileSourceType

interface ITileSourceRepository {
    fun setCurrentTileSourceType(tileSourceType: TileSourceType)
    fun getCurrentTileSourceType(): TileSourceType
}