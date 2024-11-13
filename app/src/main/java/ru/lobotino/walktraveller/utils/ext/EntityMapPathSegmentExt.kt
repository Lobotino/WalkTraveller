package ru.lobotino.walktraveller.utils.ext

import ru.lobotino.walktraveller.database.model.EntityMapPathSegment
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment

fun EntityMapPathSegment.toMapPathSegment(): MapPathSegment {
    return MapPathSegment(
        startPoint = startPoint.toMapPoint(),
        finishPoint = finishPoint.toMapPoint(),
        rating = if (rating in SegmentRating.values().indices) {
            SegmentRating.values()[rating]
        } else {
            SegmentRating.NONE
        }
    )
}