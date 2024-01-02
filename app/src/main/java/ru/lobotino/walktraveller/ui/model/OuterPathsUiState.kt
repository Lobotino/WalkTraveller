package ru.lobotino.walktraveller.ui.model

data class OuterPathsUiState(
    val showPathsButtonState: ShowPathsButtonState = ShowPathsButtonState.GONE,
    val outerPathsInfoListState: OuterPathsInfoListState = OuterPathsInfoListState.DEFAULT,
    val inSelectMode: Boolean = false
)
