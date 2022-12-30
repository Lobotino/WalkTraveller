package ru.lobotino.walktraveller.repositories

import android.content.SharedPreferences
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.repositories.interfaces.IPathRatingRepository

class PathRatingRepository(private val sharedPreferences: SharedPreferences) : IPathRatingRepository {

    companion object {
        private const val KEY_LAST_SEGMENT_RATING = "last_segment_rating"
        private val ratingList = SegmentRating.values()
    }

    override fun setCurrentRating(rating: SegmentRating) {
        sharedPreferences
            .edit()
            .putInt(KEY_LAST_SEGMENT_RATING, rating.ordinal)
            .apply()
    }

    override fun getCurrentRating(): SegmentRating {
        return ratingList[sharedPreferences.getInt(
            KEY_LAST_SEGMENT_RATING,
            SegmentRating.NORMAL.ordinal
        )]
    }
}