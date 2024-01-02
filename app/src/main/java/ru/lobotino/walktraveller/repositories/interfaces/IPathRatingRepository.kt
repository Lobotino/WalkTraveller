package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.SegmentRating

interface IPathRatingRepository {

    fun setCurrentRating(rating: SegmentRating)

    fun getCurrentRating(): SegmentRating
}
