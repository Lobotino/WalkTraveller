package ru.lobotino.walktraveller.analytics

import ru.lobotino.walktraveller.model.SegmentRating

enum class AnalyticsPathType { RATING, COMMON }

sealed class AnalyticsEvent {
    data object TrackRecordingStarted : AnalyticsEvent()
    data object TrackRecordingFinished : AnalyticsEvent()
    data class TrackShared(val pathsCount: Int) : AnalyticsEvent()
    data class RatingGiven(val rating: SegmentRating) : AnalyticsEvent()
    data class OuterPathImported(val pathsCount: Int) : AnalyticsEvent()
    data class PathShown(val pathsCount: Int, val type: AnalyticsPathType) : AnalyticsEvent()
    data class ScreenView(val screenName: String) : AnalyticsEvent()
    data class VolumeFeatureSuggest(val accepted: Boolean) : AnalyticsEvent()
}
