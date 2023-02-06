package ru.lobotino.walktraveller.ui.model

import ru.lobotino.walktraveller.model.SegmentRating

data class MapUiState(
    val isPathFinished: Boolean,
    val showPathsButtonState: ShowPathsButtonState = ShowPathsButtonState.GONE,
    val newRating: SegmentRating = SegmentRating.NORMAL,
    val bottomMenuState: BottomMenuState = BottomMenuState.DEFAULT,
    val pathsInfoListState: PathsInfoListState = PathsInfoListState.DEFAULT
)
