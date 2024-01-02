package ru.lobotino.walktraveller.ui.model

/**
 * Null means no update this state
 */
data class PathInfoItemState(
    val pathsToAction: PathsToAction,
    val showButtonState: PathInfoItemShowButtonState? = null,
    val shareButtonState: PathInfoItemShareButtonState? = null,
    val isSelected: Boolean? = null
)
