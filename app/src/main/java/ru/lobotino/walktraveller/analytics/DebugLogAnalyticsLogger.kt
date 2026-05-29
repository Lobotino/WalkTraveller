package ru.lobotino.walktraveller.analytics

import android.util.Log

class DebugLogAnalyticsLogger : IAnalyticsLogger {

    override fun log(event: AnalyticsEvent) {
        val payload = event.toFirebasePayload()
        Log.d(TAG, "event=${payload.name} params=${payload.params}")
    }

    companion object {
        private const val TAG = "Analytics"
    }
}
