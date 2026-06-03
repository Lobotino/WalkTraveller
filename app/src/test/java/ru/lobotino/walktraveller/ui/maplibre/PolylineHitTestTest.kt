package ru.lobotino.walktraveller.ui.maplibre

import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PolylineHitTestTest {

    @Test
    fun `returns null when no candidates`() {
        // given
        val tap = pt(10f, 10f)

        // when
        val result = PolylineHitTest.closestPathIdWithin(
            tap = tap,
            candidates = emptyList(),
            tolerancePx = 24f,
        )

        // then
        assertNull(result)
    }

    @Test
    fun `returns null when all candidates beyond tolerance`() {
        // given
        val tap = pt(0f, 0f)
        val candidates = listOf(
            PolylineHitTest.Candidate(
                pathId = 1L,
                vertices = listOf(pt(100f, 100f), pt(200f, 200f)),
            ),
        )

        // when
        val result = PolylineHitTest.closestPathIdWithin(
            tap = tap,
            candidates = candidates,
            tolerancePx = 24f,
        )

        // then
        assertNull(result)
    }

    @Test
    fun `returns the id whose polyline is closest to the tap`() {
        // given: two candidates — one far (id 1), one near (id 2)
        val tap = pt(50f, 50f)
        val candidates = listOf(
            PolylineHitTest.Candidate(
                pathId = 1L,
                vertices = listOf(pt(0f, 0f), pt(10f, 10f)),
            ),
            PolylineHitTest.Candidate(
                pathId = 2L,
                vertices = listOf(pt(40f, 40f), pt(60f, 60f)),
            ),
        )

        // when
        val result = PolylineHitTest.closestPathIdWithin(
            tap = tap,
            candidates = candidates,
            tolerancePx = 24f,
        )

        // then
        assertEquals(2L, result)
    }

    @Test
    fun `returns id when tap lies on the segment within tolerance`() {
        // given: a horizontal segment from (0,10) to (100,10); tap at (50,12)
        val tap = pt(50f, 12f)
        val candidates = listOf(
            PolylineHitTest.Candidate(
                pathId = 99L,
                vertices = listOf(pt(0f, 10f), pt(100f, 10f)),
            ),
        )

        // when
        val result = PolylineHitTest.closestPathIdWithin(
            tap = tap,
            candidates = candidates,
            tolerancePx = 24f,
        )

        // then
        assertEquals(99L, result)
    }

    @Test
    fun `ignores a single-vertex candidate (no segment)`() {
        // given
        val tap = pt(0f, 0f)
        val candidates = listOf(
            PolylineHitTest.Candidate(
                pathId = 1L,
                vertices = listOf(pt(0f, 0f)),
            ),
        )

        // when
        val result = PolylineHitTest.closestPathIdWithin(
            tap = tap,
            candidates = candidates,
            tolerancePx = 24f,
        )

        // then
        assertNull(result)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun pt(x: Float, y: Float) = PointF().apply { this.x = x; this.y = y }
}
