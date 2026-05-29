package ru.lobotino.walktraveller.analytics

import android.util.Log

class AnalyticsTracker(
    private val loggers: List<IAnalyticsLogger>,
) : IAnalyticsTracker {

    @Suppress("TooGenericExceptionCaught")
    override fun track(event: AnalyticsEvent) {
        for (logger in loggers) {
            try {
                logger.log(event)
            } catch (exception: Exception) {
                Log.w(TAG, "Analytics logger failed for event $event", exception)
            }
        }
    }

    companion object {
        private const val TAG = "AnalyticsTracker"
    }
}
