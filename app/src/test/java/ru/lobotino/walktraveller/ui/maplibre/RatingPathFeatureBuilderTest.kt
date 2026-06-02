package ru.lobotino.walktraveller.ui.maplibre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath

class RatingPathFeatureBuilderTest {

    private val colorOf: (SegmentRating) -> Int = { rating ->
        when (rating) {
            SegmentRating.GOOD -> 0xFF00FF00.toInt()
            SegmentRating.BADLY -> 0xFFFF0000.toInt()
            SegmentRating.PERFECT -> 0xFF0000FF.toInt()
            SegmentRating.NORMAL -> 0xFFFFFF00.toInt()
            SegmentRating.NONE -> 0xFF808080.toInt()
        }
    }

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
    fun `lerpColor returns first color at t=0`() {
        // given
        val a = 0xFF112233.toInt()
        val b = 0xFFAABBCC.toInt()

        // when
        val result = lerpColor(a, b, 0f)

        // then
        assertEquals(a, result)
    }

    @Test
    fun `lerpColor returns second color at t=1`() {
        // given
        val a = 0xFF112233.toInt()
        val b = 0xFFAABBCC.toInt()

        // when
        val result = lerpColor(a, b, 1f)

        // then
        assertEquals(b, result)
    }

    @Test
    fun `lerpColor at midpoint averages channels`() {
        // given
        val a = 0xFF000000.toInt()
        val b = 0xFFFFFFFF.toInt()

        // when
        val result = lerpColor(a, b, 0.5f)

        // then — each channel ~127
        assertEquals(0x7F, (result ushr 16) and 0xFF)
        assertEquals(0x7F, (result ushr 8) and 0xFF)
        assertEquals(0x7F, result and 0xFF)
    }

    @Test
    fun `build returns empty list for path with no segments`() {
        // given
        val path = MapRatingPath(pathId = 1L, pathSegments = emptyList())

        // when
        val result = RatingPathFeatureBuilder.build(path, blendMeters = 12f, colorOf = colorOf)

        // then
        assertEquals(emptyList<ColoredEdge>(), result)
    }

    @Test
    fun `build emits one solid edge per segment for single rating run`() {
        // given
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.002, SegmentRating.GOOD),
            ),
        )

        // when
        val result = RatingPathFeatureBuilder.build(path, blendMeters = 12f, colorOf = colorOf)

        // then
        assertEquals(2, result.size)
        result.forEach { assertEquals(colorOf(SegmentRating.GOOD), it.color) }
    }

    @Test
    fun `build subdivides edges around a junction into many sub-edges`() {
        // given — two equal-length segments, junction at midpoint, dense GPS
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.002, SegmentRating.BADLY),
            ),
        )

        // when
        val result = RatingPathFeatureBuilder.build(path, blendMeters = 12f, colorOf = colorOf)

        // then — both edges intersect the blend region; each gets split into 8 sub-edges = 16 total
        assertEquals(16, result.size)
        // first sub-edge should be the GOOD color
        assertEquals(colorOf(SegmentRating.GOOD), result.first().color)
        // last sub-edge should be the BADLY color
        assertEquals(colorOf(SegmentRating.BADLY), result.last().color)
        // some middle sub-edge should be a non-pure GOOD or BADLY color (blended)
        val middle = result[8]
        assertTrue(
            "middle sub-edge should be blended (not pure GOOD or BADLY)",
            middle.color != colorOf(SegmentRating.GOOD) && middle.color != colorOf(SegmentRating.BADLY),
        )
    }

    @Test
    fun `build adaptive blend expands for sparse segments to keep transition visible`() {
        // given — two very long segments (~111 m each), blendMeters small (12)
        // Adaptive: 0.30 * min(prevLen, nextLen) = ~33 m > 12 m → blendActual = ~33 m.
        // Each segment is subdivided into 8 sub-edges (~13.9 m each); the ~33 m blend zone
        // straddles the junction, so ~2–3 sub-edges will receive a blended color.
        // With fixed 12 m blend only ~1 sub-edge would be blended, so >= 2 proves adaptation.
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.002, SegmentRating.BADLY),
            ),
        )

        // when
        val result = RatingPathFeatureBuilder.build(path, blendMeters = 12f, colorOf = colorOf)

        // then — with adaptive blend (~33 m) wider than fixed (12 m), at least 2 sub-edges
        // should have blended (intermediate) colors, proving the adaptation is in effect.
        val intermediate = result.count {
            it.color != colorOf(SegmentRating.GOOD) && it.color != colorOf(SegmentRating.BADLY)
        }
        assertTrue("expected intermediate-color sub-edges, got $intermediate", intermediate >= 2)
    }

    @Test
    fun `build keeps far-from-junction edges solid for sparse path`() {
        // given — three segments, junctions at boundaries; middle short BADLY (~11 m) between two long GOOD/PERFECT
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.0011, SegmentRating.BADLY),
                segment(0.0, 0.0011, 0.0, 0.002, SegmentRating.PERFECT),
            ),
        )

        // when
        val result = RatingPathFeatureBuilder.build(path, blendMeters = 12f, colorOf = colorOf)

        // then — first part starts as pure GOOD, last part ends as pure PERFECT.
        assertEquals(colorOf(SegmentRating.GOOD), result.first().color)
        assertEquals(colorOf(SegmentRating.PERFECT), result.last().color)
    }

    @Test
    fun `build with subdivisions zero skips blending and emits one solid edge per non-degenerate segment`() {
        // given — two adjacent rating runs with one junction; at low LOD we want no
        // subdivided sub-edges and no blended colors at all.
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.001, SegmentRating.GOOD),
                segment(0.0, 0.001, 0.0, 0.002, SegmentRating.BADLY),
                segment(0.0, 0.002, 0.0, 0.002, SegmentRating.BADLY), // degenerate, dropped
            ),
        )

        // when
        val result = RatingPathFeatureBuilder.build(
            path = path,
            blendMeters = 12f,
            colorOf = colorOf,
            subdivisions = 0,
        )

        // then — exactly two non-degenerate edges, no blended colors.
        assertEquals(2, result.size)
        assertEquals(colorOf(SegmentRating.GOOD), result[0].color)
        assertEquals(colorOf(SegmentRating.BADLY), result[1].color)
    }

    @Test
    fun `build handles degenerate path with zero total length`() {
        // given
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(0.0, 0.0, 0.0, 0.0, SegmentRating.GOOD),
            ),
        )

        // when
        val result = RatingPathFeatureBuilder.build(path, blendMeters = 12f, colorOf = colorOf)

        // then
        assertEquals(emptyList<ColoredEdge>(), result)
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
