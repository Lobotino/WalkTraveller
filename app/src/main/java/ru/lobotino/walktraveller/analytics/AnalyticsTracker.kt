package ru.lobotino.walktraveller.analytics

import android.util.Log

class AnalyticsTracker(
    private val loggers: List<IAnalyticsLogger>,
) : IAnalyticsTracker {

    override fun track(event: AnalyticsEvent) {
        for (logger in loggers) {
            try {
                logger.log(event)
            } catch (throwable: Throwable) {
                Log.w(TAG, "Analytics logger failed for event $event", throwable)
            }
        }
    }

    companion object {
        private const val TAG = "AnalyticsTracker"
    }
}
