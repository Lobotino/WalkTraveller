package ru.lobotino.walktraveller.model

import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPoint

data class CommonPath(val pathInfo: EntityPath, val pathPoints: List<EntityPoint>)
