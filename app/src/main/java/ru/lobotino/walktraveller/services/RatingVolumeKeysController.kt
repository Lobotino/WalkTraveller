package ru.lobotino.walktraveller.services

import android.content.Context
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import ru.lobotino.walktraveller.model.SegmentRating

/**
 * Captures hardware volume-key presses without an AccessibilityService by holding an active
 * MediaSession whose playback volume is "remote": while active, volume keys are routed to
 * [VolumeProvider.onAdjustVolume] instead of changing the device volume. Active only between
 * [start] and [release].
 */
class RatingVolumeKeysController(
    private val context: Context,
    private val onRatingDetected: (SegmentRating) -> Unit,
) {

    private var mediaSession: MediaSession? = null
    private var scope: CoroutineScope? = null
    private var detector: VolumeKeysRatingDetector? = null

    fun start() {
        if (mediaSession != null) return

        val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val ratingDetector = VolumeKeysRatingDetector(controllerScope, onRatingDetected)

        val volumeProvider = object : VolumeProvider(
            VOLUME_CONTROL_RELATIVE,
            MAX_VOLUME,
            CURRENT_VOLUME
        ) {
            override fun onAdjustVolume(direction: Int) {
                when {
                    direction > 0 -> ratingDetector.onVolumeUp()
                    direction < 0 -> ratingDetector.onVolumeDown()
                }
            }
        }

        mediaSession = MediaSession(context, MEDIA_SESSION_TAG).apply {
            setCallback(object : MediaSession.Callback() {})
            setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build()
            )
            setPlaybackToRemote(volumeProvider)
            isActive = true
        }
        scope = controllerScope
        detector = ratingDetector
    }

    fun release() {
        detector?.release()
        detector = null
        scope?.cancel()
        scope = null
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
    }

    companion object {
        private const val MEDIA_SESSION_TAG = "WalkTravellerVolumeKeys"
        private const val MAX_VOLUME = 100
        private const val CURRENT_VOLUME = 50
    }
}
