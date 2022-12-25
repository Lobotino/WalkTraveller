package ru.lobotino.walktraveller.model

import ru.lobotino.walktraveller.database.model.Path
import ru.lobotino.walktraveller.database.model.Point

data class MapPath(val pathInfo: Path, val pathPoints: List<Point>)
