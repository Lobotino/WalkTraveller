package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.repositories.interfaces.IPathRatingRepository
import ru.lobotino.walktraveller.repositories.interfaces.IVibrationRepository
import ru.lobotino.walktraveller.usecases.interfaces.IPathRatingUseCase

class PathRatingUseCase(
    private val pathRatingRepository: IPathRatingRepository,
    private val vibrationRepository: IVibrationRepository,
) : IPathRatingUseCase {

    override fun setCurrentRating(rating: SegmentRating) {
        pathRatingRepository.setCurrentRating(rating)
        when (rating) {
            SegmentRating.NORMAL, SegmentRating.GOOD, SegmentRating.NONE -> vibrationRepository.vibrate(
                VIBRATION_DURATION_IN_MILLIS,
                DEFAULT_AMPLITUDE
            )

            SegmentRating.BADLY, SegmentRating.PERFECT -> vibrationRepository.vibrateDouble(
                VIBRATION_DURATION_IN_MILLIS,
                DEFAULT_AMPLITUDE
            )
        }
    }

    override fun getCurrentRating(): SegmentRating {
        return pathRatingRepository.getCurrentRating()
    }

    companion object {
        private const val VIBRATION_DURATION_IN_MILLIS = 100L
        private const val DEFAULT_AMPLITUDE = -1
    }
}
