package ru.lobotino.walktraveller.analytics

interface IAnalyticsTracker {
    fun track(event: AnalyticsEvent)
}
