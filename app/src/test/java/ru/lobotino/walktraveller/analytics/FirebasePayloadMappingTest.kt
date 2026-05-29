package ru.lobotino.walktraveller.analytics

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.lobotino.walktraveller.model.SegmentRating

class FirebasePayloadMappingTest {

    @Test
    fun `track recording started maps to event name without params`() {
        val payload = AnalyticsEvent.TrackRecordingStarted.toFirebasePayload()
        assertEquals("track_recording_started", payload.name)
        assertEquals(emptyMap<String, Any>(), payload.params)
    }

    @Test
    fun `track recording finished maps to event name without params`() {
        val payload = AnalyticsEvent.TrackRecordingFinished.toFirebasePayload()
        assertEquals("track_recording_finished", payload.name)
        assertEquals(emptyMap<String, Any>(), payload.params)
    }

    @Test
    fun `track shared maps paths count`() {
        val payload = AnalyticsEvent.TrackShared(pathsCount = 3).toFirebasePayload()
        assertEquals("track_shared", payload.name)
        assertEquals(mapOf("paths_count" to 3), payload.params)
    }

    @Test
    fun `rating given maps lowercased rating name`() {
        val payload = AnalyticsEvent.RatingGiven(SegmentRating.PERFECT).toFirebasePayload()
        assertEquals("rating_given", payload.name)
        assertEquals(mapOf("rating" to "perfect"), payload.params)
    }

    @Test
    fun `outer path imported maps paths count`() {
        val payload = AnalyticsEvent.OuterPathImported(pathsCount = 2).toFirebasePayload()
        assertEquals("outer_path_imported", payload.name)
        assertEquals(mapOf("paths_count" to 2), payload.params)
    }

    @Test
    fun `path shown maps count and lowercased type`() {
        val payload = AnalyticsEvent.PathShown(5, AnalyticsPathType.COMMON).toFirebasePayload()
        assertEquals("path_shown", payload.name)
        assertEquals(mapOf("paths_count" to 5, "path_type" to "common"), payload.params)
    }

    @Test
    fun `screen view maps screen name`() {
        val payload = AnalyticsEvent.ScreenView("map").toFirebasePayload()
        assertEquals("app_screen_view", payload.name)
        assertEquals(mapOf("screen_name" to "map"), payload.params)
    }

    @Test
    fun `volume feature suggest maps accepted as string`() {
        val payload = AnalyticsEvent.VolumeFeatureSuggest(accepted = true).toFirebasePayload()
        assertEquals("volume_feature_suggest", payload.name)
        assertEquals(mapOf("accepted" to "true"), payload.params)
    }
}
