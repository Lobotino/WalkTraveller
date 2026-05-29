package ru.lobotino.walktraveller.analytics

data class FirebasePayload(
    val name: String,
    val params: Map<String, Any>,
)

fun AnalyticsEvent.toFirebasePayload(): FirebasePayload = when (this) {
    AnalyticsEvent.TrackRecordingStarted ->
        FirebasePayload("track_recording_started", emptyMap())

    AnalyticsEvent.TrackRecordingFinished ->
        FirebasePayload("track_recording_finished", emptyMap())

    is AnalyticsEvent.TrackShared ->
        FirebasePayload("track_shared", mapOf("paths_count" to pathsCount))

    is AnalyticsEvent.RatingGiven ->
        FirebasePayload("rating_given", mapOf("rating" to rating.name.lowercase()))

    is AnalyticsEvent.OuterPathImported ->
        FirebasePayload("outer_path_imported", mapOf("paths_count" to pathsCount))

    is AnalyticsEvent.PathShown ->
        FirebasePayload(
            "path_shown",
            mapOf("paths_count" to pathsCount, "path_type" to type.name.lowercase()),
        )

    is AnalyticsEvent.ScreenView ->
        FirebasePayload("screen_view", mapOf("screen_name" to screenName))

    is AnalyticsEvent.VolumeFeatureSuggest ->
        FirebasePayload("volume_feature_suggest", mapOf("accepted" to accepted.toString()))
}
