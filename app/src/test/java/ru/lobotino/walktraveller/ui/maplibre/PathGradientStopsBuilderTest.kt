package ru.lobotino.walktraveller.ui.maplibre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath

class PathGradientStopsBuilderTest {

    @Test
    fun `haversineMeters returns zero for identical points`() {
        // given
        val point = MapPoint(latitude = 55.7558, longitude = 37.6173)

        // when
        val result = haversineMeters(point, point)

        // then
        assertEquals(0.0, result, 1e-6)
    }

    @Test
    fun `haversineMeters one degree of longitude at equator is about 111_320 meters`() {
        // given
        val a = MapPoint(latitude = 0.0, longitude = 0.0)
        val b = MapPoint(latitude = 0.0, longitude = 1.0)

        // when
        val result = haversineMeters(a, b)

        // then
        assertEquals(111_320.0, result, 111_320.0 * 0.01)
    }

    @Test
    fun `haversineMeters one degree of latitude is about 111_320 meters`() {
        // given
        val a = MapPoint(latitude = 55.0, longitude = 37.0)
        val b = MapPoint(latitude = 56.0, longitude = 37.0)

        // when
        val result = haversineMeters(a, b)

        // then
        assertEquals(111_320.0, result, 111_320.0 * 0.01)
    }

    @Test
    fun `build returns empty list for path with no segments`() {
        // given
        val path = MapRatingPath(pathId = 1L, pathSegments = emptyList())

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = 12f)

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
        val result = PathGradientStopsBuilder.build(path, blendMeters = 12f)

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
    fun `build emits four stops around a single mid-path junction`() {
        // given — two equal-length connected segments, ~111 m each, junction at midpoint
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.002, SegmentRating.BADLY),
            ),
        )
        val blend = 12f
        val totalLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.002))
        val expectedHalf = (blend / 2f) / totalLen.toFloat()

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = blend)

        // then
        assertEquals(4, result.size)
        assertEquals(0f, result[0].progress, 1e-6f)
        assertEquals(SegmentRating.GOOD, result[0].rating)
        assertEquals(0.5f - expectedHalf, result[1].progress, 1e-4f)
        assertEquals(SegmentRating.GOOD, result[1].rating)
        assertEquals(0.5f + expectedHalf, result[2].progress, 1e-4f)
        assertEquals(SegmentRating.BADLY, result[2].rating)
        assertEquals(1f, result[3].progress, 1e-6f)
        assertEquals(SegmentRating.BADLY, result[3].rating)
    }

    @Test
    fun `build clamps halfPrev when previous run shorter than half blend`() {
        // given — short GOOD run (~11 m), long BADLY run (~111 m); blend=12 → halfPrev clamped to 5.5 m
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.0001, SegmentRating.GOOD),
                segment(0.0, 0.0001, 0.0, 0.0011, SegmentRating.BADLY),
            ),
        )
        val blend = 12f
        val prevLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.0001))
        val totalLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.0011))
        val expectedHalfPrev = (prevLen / 2.0).toFloat() / totalLen.toFloat()
        val expectedHalfNext = (blend / 2f) / totalLen.toFloat()
        val expectedJunction = (prevLen / totalLen).toFloat()

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = blend)

        // then
        assertEquals(4, result.size)
        assertEquals(expectedJunction - expectedHalfPrev, result[1].progress, 1e-4f)
        assertEquals(expectedJunction + expectedHalfNext, result[2].progress, 1e-4f)
    }

    @Test
    fun `build clamps halfNext when next run shorter than half blend`() {
        // given — long GOOD run (~111 m), short BADLY run (~11 m); blend=12 → halfNext clamped to 5.5 m
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.0011, SegmentRating.BADLY),
            ),
        )
        val blend = 12f
        val prevLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.001))
        val nextLen = haversineMeters(MapPoint(0.0, 0.001), MapPoint(0.0, 0.0011))
        val totalLen = prevLen + nextLen
        val expectedHalfNext = (nextLen / 2.0).toFloat() / totalLen.toFloat()
        val expectedJunction = (prevLen / totalLen).toFloat()

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = blend)

        // then
        assertEquals(expectedJunction + expectedHalfNext, result[2].progress, 1e-4f)
    }

    @Test
    fun `build clamps adjacent halves at midpoint between two close junctions`() {
        // given — GOOD then BADLY (~11 m) then GOOD; blend zones between junctions overlap if not clamped
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.0011, SegmentRating.BADLY),
                segment(0.0, 0.0011, 0.0, 0.002, SegmentRating.GOOD),
            ),
        )
        val blend = 100f
        val len1 = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.001))
        val len2 = haversineMeters(MapPoint(0.0, 0.001), MapPoint(0.0, 0.0011))
        val totalLen = haversineMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 0.002))
        val j1 = (len1 / totalLen).toFloat()
        val j2 = ((len1 + len2) / totalLen).toFloat()
        val midHalf = ((j2 - j1) / 2f)

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = blend)

        // then — six stops total: (0,G), (j1-half,G), (j1+midHalf,B), (j2-midHalf,B), (j2+half,G), (1,G)
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
        val result = PathGradientStopsBuilder.build(path, blendMeters = 12f)

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
        val result = PathGradientStopsBuilder.build(path, blendMeters = 12f)

        // then
        result.zipWithNext().forEach { (a, b) -> assertTrue("progress must be non-decreasing", a.progress <= b.progress) }
        assertEquals(SegmentRating.GOOD, result.first().rating)
        assertEquals(SegmentRating.BADLY, result.last().rating)
    }

    @Test
    fun `build returns empty list when total length is zero`() {
        // given — every segment is degenerate, total length = 0
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.0, SegmentRating.GOOD),
            ),
        )

        // when
        val result = PathGradientStopsBuilder.build(path, blendMeters = 12f)

        // then
        assertEquals(emptyList<GradientStop>(), result)
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
