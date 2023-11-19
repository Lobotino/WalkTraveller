package ru.lobotino.walktraveller.ui.model

data class MyPathsUiState(
    val showPathsButtonState: ShowPathsButtonState = ShowPathsButtonState.GONE,
    val showPathsFilterButtonState: ShowPathsFilterButtonState = ShowPathsFilterButtonState.GONE,
    val pathsInfoListState: MyPathsInfoListState = MyPathsInfoListState.DEFAULT,
    val inSelectMode: Boolean = false
)