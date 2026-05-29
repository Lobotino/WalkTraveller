package ru.lobotino.walktraveller

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.services.VolumeKeysRatingDetector

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeKeysRatingDetectorTest {

    @Test
    fun `single volume down sets NORMAL only after the delay`() = runTest {
        val results = mutableListOf<SegmentRating>()
        val detector = VolumeKeysRatingDetector(backgroundScope) { results.add(it) }

        detector.onVolumeDown()
        assertTrue("nothing should fire before the delay elapses", results.isEmpty())

        advanceUntilIdle()
        assertEquals(listOf(SegmentRating.NORMAL), results)
    }

    @Test
    fun `double volume down sets BADLY and never NORMAL`() = runTest {
        val results = mutableListOf<SegmentRating>()
        val detector = VolumeKeysRatingDetector(backgroundScope) { results.add(it) }

        detector.onVolumeDown()
        detector.onVolumeDown()
        advanceUntilIdle()

        assertEquals(listOf(SegmentRating.BADLY), results)
    }

    @Test
    fun `single volume up sets GOOD only after the delay`() = runTest {
        val results = mutableListOf<SegmentRating>()
        val detector = VolumeKeysRatingDetector(backgroundScope) { results.add(it) }

        detector.onVolumeUp()
        assertTrue(results.isEmpty())

        advanceUntilIdle()
        assertEquals(listOf(SegmentRating.GOOD), results)
    }

    @Test
    fun `double volume up sets PERFECT and never GOOD`() = runTest {
        val results = mutableListOf<SegmentRating>()
        val detector = VolumeKeysRatingDetector(backgroundScope) { results.add(it) }

        detector.onVolumeUp()
        detector.onVolumeUp()
        advanceUntilIdle()

        assertEquals(listOf(SegmentRating.PERFECT), results)
    }

    @Test
    fun `opposite key press cancels the pending rating`() = runTest {
        val results = mutableListOf<SegmentRating>()
        val detector = VolumeKeysRatingDetector(backgroundScope) { results.add(it) }

        detector.onVolumeDown()
        detector.onVolumeUp()
        advanceUntilIdle()

        assertEquals(listOf(SegmentRating.GOOD), results)
    }

    @Test
    fun `release cancels pending rating`() = runTest {
        val results = mutableListOf<SegmentRating>()
        val detector = VolumeKeysRatingDetector(backgroundScope) { results.add(it) }

        detector.onVolumeDown()
        detector.release()
        advanceUntilIdle()

        assertTrue(results.isEmpty())
    }
}
