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

        // The MediaSession is created on the thread that hosts this controller (the service's
        // main thread), so onAdjustVolume is delivered on the main thread. Confining the
        // detector's coroutines to the main thread keeps all of its state single-threaded.
        val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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

        // Nominal values: with VOLUME_CONTROL_RELATIVE only the adjustment direction is used,
        // the absolute volume level is irrelevant.
        private const val MAX_VOLUME = 100
        private const val CURRENT_VOLUME = 50
    }
}
