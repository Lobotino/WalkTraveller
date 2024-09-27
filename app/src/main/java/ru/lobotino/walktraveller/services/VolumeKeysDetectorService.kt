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
import ru.lobotino.walktraveller.repositories.VibrationRepository
import ru.lobotino.walktraveller.repositories.WritingPathStatesRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.usecases.PathRatingUseCase
import ru.lobotino.walktraveller.usecases.interfaces.IPathRatingUseCase

class VolumeKeysDetectorService : AccessibilityService() {

    companion object {
        private val TAG = VolumeKeysDetectorService::class.java.canonicalName
        private const val BEFORE_CHANGE_RATING_DELAY = 500L
        private const val PACKAGE_NAME = ContactsContract.Directory.PACKAGE_NAME
        const val RATING_CHANGES_BROADCAST = "$PACKAGE_NAME.rating_broadcast"
    }

    private var downRatingJob: Job? = null
    private var upRatingJob: Job? = null

    private var pathRatingUseCase: IPathRatingUseCase? = null
    private var writingPathStatesRepository: IWritingPathStatesRepository? = null

    override fun onCreate() {
        super.onCreate()
        val sharedPreferences = getSharedPreferences(
            App.SHARED_PREFS_TAG,
            AppCompatActivity.MODE_PRIVATE
        )
        pathRatingUseCase = PathRatingUseCase(
            PathRatingRepository(sharedPreferences),
            VibrationRepository(applicationContext)
        )
        writingPathStatesRepository = WritingPathStatesRepository(sharedPreferences)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (writingPathStatesRepository?.isWritingPathNow() != true) return false

        if (event.action == ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    upRatingJob?.cancel()
                    if (downRatingJob?.isActive == true) {
                        downRatingJob?.cancel()
                        pathRatingUseCase?.setCurrentRating(SegmentRating.BADLY)
                        broadcastRatingChanged()
                        Log.d(TAG, "Set current rating to BADLY")
                        return true
                    } else {
                        downRatingJob = CoroutineScope(Dispatchers.Default).launch {
                            delay(BEFORE_CHANGE_RATING_DELAY)
                            pathRatingUseCase?.setCurrentRating(SegmentRating.NORMAL)
                            downRatingJob = null
                            broadcastRatingChanged()
                            Log.d(TAG, "Set current rating to NORMAL")
                        }
                        return true
                    }
                }

                KeyEvent.KEYCODE_VOLUME_UP -> {
                    downRatingJob?.cancel()
                    if (upRatingJob?.isActive == true) {
                        upRatingJob?.cancel()
                        pathRatingUseCase?.setCurrentRating(SegmentRating.PERFECT)
                        broadcastRatingChanged()
                        Log.d(TAG, "Set current rating to PERFECT")
                        return true
                    } else {
                        upRatingJob = CoroutineScope(Dispatchers.Default).launch {
                            delay(BEFORE_CHANGE_RATING_DELAY)
                            pathRatingUseCase?.setCurrentRating(SegmentRating.GOOD)
                            upRatingJob = null
                            broadcastRatingChanged()
                            Log.d(TAG, "Set current rating to GOOD")
                        }
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun broadcastRatingChanged() {
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(Intent(RATING_CHANGES_BROADCAST))
    }
}
