package ru.lobotino.walktraveller.ui.model

import ru.lobotino.walktraveller.model.map.MapPathInfo

data class PathInfoItemModel(
    val pathInfo: MapPathInfo,
    var pathInfoItemState: PathInfoItemState = PathInfoItemState.DEFAULT,
    var isSelected: Boolean = false
)
