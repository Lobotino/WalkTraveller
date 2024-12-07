package ru.lobotino.walktraveller.repositories

import android.content.SharedPreferences
import ru.lobotino.walktraveller.model.TileSourceType
import ru.lobotino.walktraveller.repositories.interfaces.ITileSourceRepository

class TileSourceRepository(
    private val sharedPreferences: SharedPreferences,
) : ITileSourceRepository {

    override fun setCurrentTileSourceType(tileSourceType: TileSourceType) {
        sharedPreferences.edit().apply {
            putString(KEY_CURRENT_TILE_SOURCE, tileSourceType.name)
            apply()
        }
    }

    override fun getCurrentTileSourceType(): TileSourceType {
        val savedValue =
            sharedPreferences.getString(KEY_CURRENT_TILE_SOURCE, DefaultTileSource.name)
                ?: DefaultTileSource.name

        return TileSourceType.valueOf(savedValue)
    }

    companion object {
        private const val KEY_CURRENT_TILE_SOURCE = "CURRENT_TILE_SOURCE"
        private val DefaultTileSource = TileSourceType.OSM_MAPNIK
    }
}
