package ru.lobotino.walktraveller.ui.model

import ru.lobotino.walktraveller.model.map.MapPathInfo

data class PathInfoItemModel(
    val pathInfo: MapPathInfo,
    var showButtonState: PathInfoItemShowButtonState = PathInfoItemShowButtonState.DEFAULT,
    var shareButtonState: PathInfoItemShareButtonState = PathInfoItemShareButtonState.DEFAULT,
    var isSelected: Boolean = false
)
