package ru.lobotino.walktraveller.model

import org.osmdroid.tileprovider.tilesource.ITileSource

sealed class TileSource {
    class OSMTileSource(val tileSource: ITileSource) : TileSource()
    // TODO 2gis, yandex, google...
}
