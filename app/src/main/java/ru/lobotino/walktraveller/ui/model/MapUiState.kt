package ru.lobotino.walktraveller.ui.model

import ru.lobotino.walktraveller.model.map.MapPoint

data class MapUiState(
    val isWritePath: Boolean,
    val isPathFinished: Boolean,
    val needToClearMapNow: Boolean,
    val mapCenter: MapPoint?,
    val showPathsButtonState: ShowPathsButtonState = ShowPathsButtonState.DEFAULT
)
