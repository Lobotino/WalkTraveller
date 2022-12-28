package ru.lobotino.walktraveller.model

import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPoint

data class MapPath(val pathInfo: EntityPath, val pathPoints: List<EntityPoint>)
