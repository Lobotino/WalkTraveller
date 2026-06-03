package ru.lobotino.walktraveller.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lobotino.walktraveller.model.SegmentRating

class WelcomeTrackGeometryTest {

    private val sut = WelcomeTrackGeometry

    @Test
    fun `ratingForSegment cycles through palette and never returns NONE`() {
        // given
        val ratings = (0 until 8).map { sut.ratingForSegment(it) }

        // then
        assertEquals(SegmentRating.BADLY, ratings[0])
        assertEquals(SegmentRating.NORMAL, ratings[1])
        assertEquals(SegmentRating.GOOD, ratings[2])
        assertEquals(SegmentRating.PERFECT, ratings[3])
        assertEquals(SegmentRating.BADLY, ratings[4])
        assertTrue(ratings.none { it == SegmentRating.NONE })
    }

    @Test
    fun `visibleSegmentCount clamps progress and scales between 0 and segmentCount`() {
        // then
        assertEquals(0, sut.visibleSegmentCount(0f))
        assertEquals(0, sut.visibleSegmentCount(-1f))
        assertEquals(sut.segmentCount, sut.visibleSegmentCount(1f))
        assertEquals(sut.segmentCount, sut.visibleSegmentCount(2f))
        assertTrue(sut.visibleSegmentCount(0.5f) > 0)
        assertTrue(sut.visibleSegmentCount(0.5f) < sut.segmentCount)
    }

    @Test
    fun `headPosition moves from first to last point`() {
        // given
        val start = sut.headPosition(0f)
        val end = sut.headPosition(1f)
        val middle = sut.headPosition(0.5f)

        // then
        assertEquals(sut.trackPoints.first(), start)
        assertEquals(sut.trackPoints.last(), end)
        assertNotEquals(start, middle)
        assertNotEquals(end, middle)
    }

    @Test
    fun `track has many segments`() {
        // then: "add more segments" — track must be visually dense
        assertTrue("expected a dense track", sut.segmentCount >= 10)
    }
}
