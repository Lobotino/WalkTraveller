package ru.lobotino.walktraveller.ui.model

import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.ui.maplibre.PathBounds

sealed class MapEvent {
    object ClearMap : MapEvent()

    class ShowRatingPathList(val pathList: List<MapRatingPath>) : MapEvent()

    class ShowCommonPathList(val pathList: List<MapCommonPath>) : MapEvent()

    class ShowRatingPath(val path: MapRatingPath) : MapEvent()

    class ShowCommonPath(val path: MapCommonPath) : MapEvent()

    class HidePath(val pathsToHide: PathsToAction) : MapEvent()

    class BottomMenuStateChange(val newBottomMenuState: BottomMenuState) : MapEvent()

    class FitCameraToBounds(val bounds: PathBounds) : MapEvent()

    class SetFocusedPath(val pathId: Long?) : MapEvent()
}
