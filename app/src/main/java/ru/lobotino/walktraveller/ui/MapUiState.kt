package ru.lobotino.walktraveller.ui

import ru.lobotino.walktraveller.model.MapPoint

data class MapUiState(
    val isWritePath: Boolean,
    val isPathFinished: Boolean,
    val needToClearMapNow: Boolean,
    val mapCenter: MapPoint?
)
