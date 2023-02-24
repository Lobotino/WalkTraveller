package ru.lobotino.walktraveller.ui.model

data class PathInfoItemState(
    val pathId: Long,
    val showButtonState: PathInfoItemShowButtonState,
    val isDeleted: Boolean = false
)