package ru.lobotino.walktraveller.model

import ru.lobotino.walktraveller.database.model.EntityPoint

data class PathSegment(
    val startPoint: EntityPoint,
    val finishPoint: EntityPoint,
    val color: String
)
