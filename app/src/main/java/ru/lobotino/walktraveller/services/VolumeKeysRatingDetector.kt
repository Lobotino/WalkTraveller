package ru.lobotino.walktraveller.services

import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.lobotino.walktraveller.model.SegmentRating

/**
 * Single/double volume-key tap detection for path rating.
 * 1 tap (after the debounce window) = NORMAL/GOOD, 2 taps within the window = BADLY/PERFECT.
 * Pressing the opposite direction cancels the pending single-tap rating.
 *
 * Coroutines are launched on the dispatcher extracted from [scope] so that in tests the
 * virtual-time scheduler is respected while the coroutines remain foreground work (i.e.
 * `advanceUntilIdle()` advances them even when [scope] is a `backgroundScope`).
 */
class VolumeKeysRatingDetector(
    scope: CoroutineScope,
    private val onRatingDetected: (SegmentRating) -> Unit,
) {

    private val launchScope: CoroutineScope = CoroutineScope(
        (scope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher)
            ?: Dispatchers.Default,
    )

    private var downRatingJob: Job? = null
    private var upRatingJob: Job? = null

    fun onVolumeDown() {
        upRatingJob?.cancel()
        if (downRatingJob?.isActive == true) {
            downRatingJob?.cancel()
            downRatingJob = null
            onRatingDetected(SegmentRating.BADLY)
        } else {
            downRatingJob = launchScope.launch {
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
            upRatingJob = launchScope.launch {
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
