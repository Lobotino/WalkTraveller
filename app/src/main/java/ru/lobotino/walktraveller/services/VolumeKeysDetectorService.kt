package ru.lobotino.walktraveller.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.ContactsContract
import android.util.Log
import android.view.KeyEvent
import android.view.KeyEvent.ACTION_DOWN
import android.view.accessibility.AccessibilityEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.repositories.PathRatingRepository


class VolumeKeysDetectorService : AccessibilityService() {

    companion object {
        private val TAG = VolumeKeysDetectorService::class.java.canonicalName
        private const val BEFORE_CHANGE_RATING_DELAY = 500L
        private const val PACKAGE_NAME = ContactsContract.Directory.PACKAGE_NAME
        const val RATING_CHANGES_BROADCAST = "$PACKAGE_NAME.rating_broadcast"
    }

    private var downRatingJob: Job? = null
    private var upRatingJob: Job? = null

    private var pathRatingRepository: PathRatingRepository? = null

    override fun onCreate() {
        super.onCreate()
        pathRatingRepository = PathRatingRepository(
            getSharedPreferences(
                App.SHARED_PREFS_TAG,
                AppCompatActivity.MODE_PRIVATE
            )
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    upRatingJob?.cancel()
                    if (downRatingJob?.isActive == true) {
                        downRatingJob?.cancel()
                        pathRatingRepository?.setCurrentRating(SegmentRating.BADLY)
                        broadcastRatingChanged()
                        Log.d(TAG, "Set current rating to BADLY")
                    } else {
                        downRatingJob = CoroutineScope(Dispatchers.Default).launch {
                            delay(BEFORE_CHANGE_RATING_DELAY)
                            pathRatingRepository?.setCurrentRating(SegmentRating.NORMAL)
                            downRatingJob = null
                            broadcastRatingChanged()
                            Log.d(TAG, "Set current rating to NORMAL")
                        }
                    }
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    downRatingJob?.cancel()
                    if (upRatingJob?.isActive == true) {
                        upRatingJob?.cancel()
                        pathRatingRepository?.setCurrentRating(SegmentRating.PERFECT)
                        broadcastRatingChanged()
                        Log.d(TAG, "Set current rating to PERFECT")
                    } else {
                        upRatingJob = CoroutineScope(Dispatchers.Default).launch {
                            delay(BEFORE_CHANGE_RATING_DELAY)
                            pathRatingRepository?.setCurrentRating(SegmentRating.GOOD)
                            upRatingJob = null
                            broadcastRatingChanged()
                            Log.d(TAG, "Set current rating to GOOD")
                        }
                    }
                }
            }
        }
        return super.onKeyEvent(event)
    }

    private fun broadcastRatingChanged() {
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(Intent(RATING_CHANGES_BROADCAST))
    }
}