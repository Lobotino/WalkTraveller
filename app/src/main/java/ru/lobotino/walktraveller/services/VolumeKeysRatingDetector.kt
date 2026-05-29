package ru.lobotino.walktraveller.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.lobotino.walktraveller.model.SegmentRating

/**
 * Single/double volume-key tap detection for path rating.
 * 1 tap (after the debounce window) = NORMAL/GOOD, 2 taps within the window = BADLY/PERFECT.
 * Pressing the opposite direction cancels the pending single-tap rating.
 */
class VolumeKeysRatingDetector(
    private val scope: CoroutineScope,
    private val onRatingDetected: (SegmentRating) -> Unit,
) {

    private var downRatingJob: Job? = null
    private var upRatingJob: Job? = null

    fun onVolumeDown() {
        upRatingJob?.cancel()
        if (downRatingJob?.isActive == true) {
            downRatingJob?.cancel()
            downRatingJob = null
            onRatingDetected(SegmentRating.BADLY)
        } else {
            downRatingJob = scope.launch {
                delay(BEFORE_CHANGE_RATING_DELAY)
                downRatingJob = null
                onRatingDetected(SegmentRating.NORMAL)
            }
        }
    }

    fun onVolumeUp() {
        downRatingJob?.cancel()
        if (upRatingJob?.isActive == true) {
            upRatingJob?.cancel()
            upRatingJob = null
            onRatingDetected(SegmentRating.PERFECT)
        } else {
            upRatingJob = scope.launch {
                delay(BEFORE_CHANGE_RATING_DELAY)
                upRatingJob = null
                onRatingDetected(SegmentRating.GOOD)
            }
        }
    }

    fun release() {
        downRatingJob?.cancel()
        upRatingJob?.cancel()
        downRatingJob = null
        upRatingJob = null
    }

    companion object {
        const val BEFORE_CHANGE_RATING_DELAY = 500L
    }
}
