package ru.lobotino.walktraveller.ui.maplibre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath

class PathGradientStopsBuilderTest {

    private val sut = PathGradientStopsBuilder

    @Test
    fun `build returns empty list for path with no segments`() {
        // given
        val path = MapRatingPath(pathId = 1L, pathSegments = emptyList())

        // when
        val result = sut.build(path, blendMeters = 12f)

        // then
        assertEquals(emptyList<GradientStop>(), result)
    }

    @Test
    fun `build returns empty list when total length is zero`() {
        // given — degenerate single segment, total length 0
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.0, SegmentRating.GOOD),
            ),
        )

        // when
        val result = sut.build(path, blendMeters = 12f)

        // then
        assertEquals(emptyList<GradientStop>(), result)
    }

    @Test
    fun `build returns endpoints only for single rating run`() {
        // given
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.002, SegmentRating.GOOD),
            ),
        )

        // when
        val result = sut.build(path, blendMeters = 12f)

        // then
        assertEquals(
            listOf(
                GradientStop(0f, SegmentRating.GOOD),
                GradientStop(1f, SegmentRating.GOOD),
            ),
            result,
        )
    }

    @Test
    fun `build adaptive blend widens halves around junction for sparse runs`() {
        // given — two ~111 m runs. Fixed blendMeters=12 → halfBlend=6 m. Adaptive widens to
        // max(12, 0.30 * min(111, 111)) ≈ 33 m → halfBlend ≈ 16.65 m. Verify the resulting
        // half on each side of the junction is at least 2.5x the fixed-only value.
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.002, SegmentRating.BADLY),
            ),
        )
        val totalLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.002))
        val fixedHalfFraction = (12f / 2f) / totalLen.toFloat()

        // when
        val result = sut.build(path, blendMeters = 12f)

        // then
        assertEquals(4, result.size)
        val junction = 0.5f
        val halfPrev = junction - result[1].progress
        val halfNext = result[2].progress - junction
        assertTrue(
            "adaptive halfPrev=$halfPrev should exceed 2.5 * fixed=$fixedHalfFraction",
            halfPrev > 2.5f * fixedHalfFraction,
        )
        assertTrue(
            "adaptive halfNext=$halfNext should exceed 2.5 * fixed=$fixedHalfFraction",
            halfNext > 2.5f * fixedHalfFraction,
        )
    }

    @Test
    fun `build uses fixed blendMeters when 30 percent of shorter run is smaller`() {
        // given — two ~111 m runs, blendMeters = 100 → adaptive = max(100, 0.30 * 111 ≈ 33) = 100.
        // halfBlend = 50 m, clamped by runLen/2 = 55.5 m, so halfPrev = halfNext = 50 m.
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.002, SegmentRating.BADLY),
            ),
        )
        val totalLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.002))
        val expectedHalf = (50.0 / totalLen).toFloat()

        // when
        val result = sut.build(path, blendMeters = 100f)

        // then
        assertEquals(4, result.size)
        assertEquals(0.5f - expectedHalf, result[1].progress, 1e-3f)
        assertEquals(0.5f + expectedHalf, result[2].progress, 1e-3f)
        assertEquals(SegmentRating.GOOD, result[1].rating)
        assertEquals(SegmentRating.BADLY, result[2].rating)
    }

    @Test
    fun `build clamps halfPrev when previous run shorter than half adaptive blend`() {
        // given — short GOOD (~11 m), long BADLY (~111 m). blendMeters=12 →
        // adaptive = max(12, 0.30 * min(11, 111)) = max(12, 3.33) = 12 m. halfBlend = 6.
        // halfPrev = min(6, 11/2 = 5.5) = 5.5 m. halfNext = 6 m.
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.0001, SegmentRating.GOOD),
                segment(0.0, 0.0001, 0.0, 0.0011, SegmentRating.BADLY),
            ),
        )
        val prevLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.0001))
        val totalLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.0011))
        val expectedHalfPrev = (prevLen / 2.0).toFloat() / totalLen.toFloat()
        val expectedHalfNext = (6.0 / totalLen).toFloat()
        val expectedJunction = (prevLen / totalLen).toFloat()

        // when
        val result = sut.build(path, blendMeters = 12f)

        // then
        assertEquals(4, result.size)
        assertEquals(expectedJunction - expectedHalfPrev, result[1].progress, 1e-4f)
        assertEquals(expectedJunction + expectedHalfNext, result[2].progress, 1e-4f)
    }

    @Test
    fun `build clamps halfNext when next run shorter than half adaptive blend`() {
        // given — long GOOD (~111 m), short BADLY (~11 m). Adaptive = max(12, 0.30 * 11) = 12.
        // halfBlend = 6. halfNext = min(6, 11/2 = 5.5) = 5.5 m.
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.0011, SegmentRating.BADLY),
            ),
        )
        val prevLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.001))
        val nextLen = haversineMeters(MapPoint(0.0, 0.001), MapPoint(0.0, 0.0011))
        val totalLen = prevLen + nextLen
        val expectedHalfNext = (nextLen / 2.0).toFloat() / totalLen.toFloat()
        val expectedJunction = (prevLen / totalLen).toFloat()

        // when
        val result = sut.build(path, blendMeters = 12f)

        // then
        assertEquals(expectedJunction + expectedHalfNext, result[2].progress, 1e-4f)
    }

    @Test
    fun `build clamps adjacent halves at midpoint between two close junctions`() {
        // given — long GOOD, very short BADLY (~11 m), long GOOD again. blendMeters=100
        // → adaptive ≈ max(100, 0.30 * 11) = 100 m. Half=50 m would overlap; should clamp
        // to (BADLY run len)/2 on each side of each junction.
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.0011, SegmentRating.BADLY),
                segment(0.0, 0.0011, 0.0, 0.002, SegmentRating.GOOD),
            ),
        )
        val len1 = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.001))
        val len2 = haversineMeters(MapPoint(0.0, 0.001), MapPoint(0.0, 0.0011))
        val totalLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.002))
        val j1 = (len1 / totalLen).toFloat()
        val j2 = ((len1 + len2) / totalLen).toFloat()
        val midHalf = (j2 - j1) / 2f

        // when
        val result = sut.build(path, blendMeters = 100f)

        // then — six stops: (0,G), (j1-half,G), (j1+midHalf,B), (j2-midHalf,B), (j2+half,G), (1,G)
        assertEquals(6, result.size)
        assertEquals(j1 + midHalf, result[2].progress, 1e-4f)
        assertEquals(j2 - midHalf, result[3].progress, 1e-4f)
        assertEquals(SegmentRating.BADLY, result[2].rating)
        assertEquals(SegmentRating.BADLY, result[3].rating)
    }

    @Test
    fun `build emits six stops for three runs in order`() {
        // given
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.PERFECT),
                segment(0.0, 0.001, 0.0, 0.002, SegmentRating.BADLY),
                segment(0.0, 0.002, 0.0, 0.003, SegmentRating.PERFECT),
            ),
        )

        // when
        val result = sut.build(path, blendMeters = 12f)

        // then
        assertEquals(6, result.size)
        assertEquals(
            listOf(
                SegmentRating.PERFECT,
                SegmentRating.PERFECT,
                SegmentRating.BADLY,
                SegmentRating.BADLY,
                SegmentRating.PERFECT,
                SegmentRating.PERFECT,
            ),
            result.map { it.rating },
        )
    }

    @Test
    fun `build dedupes zero-length joins and keeps progress monotonic`() {
        // given — two consecutive identical points in the middle (zero-length segment)
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.002, SegmentRating.BADLY),
            ),
        )

        // when
        val result = sut.build(path, blendMeters = 12f)

        // then
        result.zipWithNext().forEach { (a, b) ->
            assertTrue("progress must be non-decreasing", a.progress <= b.progress)
        }
        assertEquals(SegmentRating.GOOD, result.first().rating)
        assertEquals(SegmentRating.BADLY, result.last().rating)
    }

    private fun segment(
        startLat: Double,
        startLon: Double,
        finishLat: Double,
        finishLon: Double,
        rating: SegmentRating,
    ) = MapPathSegment(
        startPoint = MapPoint(startLat, startLon),
        finishPoint = MapPoint(finishLat, finishLon),
        rating = rating,
    )
}
