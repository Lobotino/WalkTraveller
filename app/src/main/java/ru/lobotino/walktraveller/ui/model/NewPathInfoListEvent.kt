package ru.lobotino.walktraveller.ui.model

import ru.lobotino.walktraveller.model.map.MapPathInfo

data class NewPathInfoListEvent(val pathsMenuType: PathsMenuType, val newPathInfoList: List<MapPathInfo>)
