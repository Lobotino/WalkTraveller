package ru.lobotino.walktraveller.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsTrackerTest {

    private class RecordingAnalyticsLogger : IAnalyticsLogger {
        val events = mutableListOf<AnalyticsEvent>()
        override fun log(event: AnalyticsEvent) {
            events.add(event)
        }
    }

    private class ThrowingAnalyticsLogger : IAnalyticsLogger {
        override fun log(event: AnalyticsEvent) {
            throw IllegalStateException("boom")
        }
    }

    @Test
    fun `event is delivered to every logger`() {
        val first = RecordingAnalyticsLogger()
        val second = RecordingAnalyticsLogger()
        val tracker = AnalyticsTracker(listOf(first, second))

        tracker.track(AnalyticsEvent.TrackRecordingStarted)

        assertEquals(listOf(AnalyticsEvent.TrackRecordingStarted), first.events)
        assertEquals(listOf(AnalyticsEvent.TrackRecordingStarted), second.events)
    }

    @Test
    fun `a failing logger does not stop the others`() {
        val healthy = RecordingAnalyticsLogger()
        val tracker = AnalyticsTracker(listOf(ThrowingAnalyticsLogger(), healthy))

        tracker.track(AnalyticsEvent.TrackShared(pathsCount = 1))

        assertEquals(listOf(AnalyticsEvent.TrackShared(pathsCount = 1)), healthy.events)
    }
}
