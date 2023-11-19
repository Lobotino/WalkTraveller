package ru.lobotino.walktraveller.ui.model

/**
 * Null means no update this state
 */
data class PathInfoItemState(
    val pathId: Long,
    val showButtonState: PathInfoItemShowButtonState? = null,
    val shareButtonState: PathInfoItemShareButtonState? = null,
    val isSelected: Boolean? = null
)