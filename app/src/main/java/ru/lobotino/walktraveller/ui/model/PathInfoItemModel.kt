package ru.lobotino.walktraveller.ui.model

import ru.lobotino.walktraveller.model.map.MapPathInfo

data class PathInfoItemModel(
    val pathInfo: MapPathInfo,
    var pathInfoItemShowButtonState: PathInfoItemShowButtonState = PathInfoItemShowButtonState.DEFAULT,
    var isSelected: Boolean = false
)
