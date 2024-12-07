package ru.lobotino.walktraveller.usecases.interfaces

import ru.lobotino.walktraveller.model.SegmentRating

interface IPathRatingUseCase {
    fun setCurrentRating(rating: SegmentRating)
    fun getCurrentRating(): SegmentRating
}
