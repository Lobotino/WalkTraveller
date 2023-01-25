package ru.lobotino.walktraveller.ui.model

import ru.lobotino.walktraveller.model.SegmentRating

data class MapUiState(
    val isWritePath: Boolean,
    val isPathFinished: Boolean,
    val showPathsButtonState: ShowPathsButtonState = ShowPathsButtonState.DEFAULT,
    val newRating: SegmentRating = SegmentRating.NORMAL,
    val bottomMenuState: BottomMenuState = BottomMenuState.DEFAULT,
    val pathsInfoListState: PathsInfoListState = PathsInfoListState.DEFAULT
)
