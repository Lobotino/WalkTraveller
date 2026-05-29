package ru.lobotino.walktraveller.analytics

interface IAnalyticsLogger {
    fun log(event: AnalyticsEvent)
}
