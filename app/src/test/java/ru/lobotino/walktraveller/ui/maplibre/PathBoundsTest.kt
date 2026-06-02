package ru.lobotino.walktraveller.ui.maplibre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath

class PathBoundsTest {

    @Test
    fun `pathBounds returns null for path with no segments`() {
        // given
        val path = MapRatingPath(pathId = 1L, pathSegments = emptyList())

        // when
        val result = pathBounds(path)

        // then
        assertNull(result)
    }

    @Test
    fun `pathBounds for a single segment encloses both endpoints`() {
        // given
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(segment(10.0, 20.0, 30.0, 40.0)),
        )

        // when
        val result = pathBounds(path)

        // then
        assertEquals(PathBounds(minLat = 10.0, maxLat = 30.0, minLng = 20.0, maxLng = 40.0), result)
    }

    @Test
    fun `pathBounds for multiple segments encloses all points`() {
        // given
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(
                segment(10.0, 20.0, 30.0, 40.0),
                segment(30.0, 40.0, -5.0, 100.0),
                segment(-5.0, 100.0, 50.0, 15.0),
            ),
        )

        // when
        val result = pathBounds(path)

        // then
        assertEquals(PathBounds(minLat = -5.0, maxLat = 50.0, minLng = 15.0, maxLng = 100.0), result)
    }

    @Test
    fun `pathBounds for degenerate single-point path returns zero-area bounds at that point`() {
        // given
        val path = MapRatingPath(
            pathId = 1L,
            pathSegments = listOf(segment(55.0, 37.0, 55.0, 37.0)),
        )

        // when
        val result = pathBounds(path)

        // then
        assertEquals(PathBounds(minLat = 55.0, maxLat = 55.0, minLng = 37.0, maxLng = 37.0), result)
    }

    @Test
    fun `intersects returns true for overlapping bounds`() {
        // given
        val a = PathBounds(0.0, 10.0, 0.0, 10.0)
        val b = PathBounds(5.0, 15.0, 5.0, 15.0)

        // when / then
        assertTrue(a.intersects(b))
        assertTrue(b.intersects(a))
    }

    @Test
    fun `intersects returns true for identical bounds`() {
        // given
        val a = PathBounds(0.0, 10.0, 0.0, 10.0)

        // when / then
        assertTrue(a.intersects(a))
    }

    @Test
    fun `intersects returns true for one bounds fully inside another`() {
        // given
        val outer = PathBounds(0.0, 100.0, 0.0, 100.0)
        val inner = PathBounds(40.0, 60.0, 40.0, 60.0)

        // when / then
        assertTrue(outer.intersects(inner))
        assertTrue(inner.intersects(outer))
    }

    @Test
    fun `intersects returns true when bounds share only an edge`() {
        // given
        val a = PathBounds(0.0, 10.0, 0.0, 10.0)
        val b = PathBounds(10.0, 20.0, 5.0, 15.0)

        // when / then — touching at lat=10 still counts as intersecting (inclusive)
        assertTrue(a.intersects(b))
    }

    @Test
    fun `intersects returns false when disjoint by latitude`() {
        // given
        val a = PathBounds(0.0, 10.0, 0.0, 10.0)
        val b = PathBounds(20.0, 30.0, 0.0, 10.0)

        // when / then
        assertFalse(a.intersects(b))
        assertFalse(b.intersects(a))
    }

    @Test
    fun `intersects returns false when disjoint by longitude`() {
        // given
        val a = PathBounds(0.0, 10.0, 0.0, 10.0)
        val b = PathBounds(0.0, 10.0, 20.0, 30.0)

        // when / then
        assertFalse(a.intersects(b))
        assertFalse(b.intersects(a))
    }

    @Test
    fun `expand grows bounds in all four directions by given margins`() {
        // given
        val b = PathBounds(minLat = 10.0, maxLat = 20.0, minLng = 30.0, maxLng = 40.0)

        // when
        val result = b.expand(latMargin = 2.0, lngMargin = 3.0)

        // then
        assertEquals(PathBounds(minLat = 8.0, maxLat = 22.0, minLng = 27.0, maxLng = 43.0), result)
    }

    @Test
    fun `expand with zero margins returns equal bounds`() {
        // given
        val b = PathBounds(1.0, 2.0, 3.0, 4.0)

        // when
        val result = b.expand(latMargin = 0.0, lngMargin = 0.0)

        // then
        assertEquals(b, result)
    }

    private fun segment(
        startLat: Double,
        startLng: Double,
        finishLat: Double,
        finishLng: Double,
    ) = MapPathSegment(
        startPoint = MapPoint(startLat, startLng),
        finishPoint = MapPoint(finishLat, finishLng),
        rating = SegmentRating.GOOD,
    )
}
