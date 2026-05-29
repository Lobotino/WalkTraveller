package ru.lobotino.walktraveller.usecases

import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IPathDistancesRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathsLoaderRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathsLoaderVersionHelper
import java.io.IOException

class OuterPathsInteractorTest {

    private val uri = mockk<Uri>()
    private val segment = MapPathSegment(MapPoint(1.0, 1.0), MapPoint(2.0, 2.0), SegmentRating.GOOD)

    private lateinit var pathsLoaderVersionHelper: IPathsLoaderVersionHelper
    private lateinit var pathsLoaderRepositoryV1: IPathsLoaderRepository
    private lateinit var pathDistancesRepository: IPathDistancesRepository
    private lateinit var pathRepository: IPathRepository
    private lateinit var sut: OuterPathsInteractor

    @Before
    fun setUp() {
        pathsLoaderVersionHelper = mockk()
        pathsLoaderRepositoryV1 = mockk()
        pathDistancesRepository = mockk()
        pathRepository = mockk()
        sut = OuterPathsInteractor(
            pathsLoaderVersionHelper,
            pathsLoaderRepositoryV1,
            pathDistancesRepository,
            pathRepository
        )
    }

    @Test
    fun `getAllPaths returns empty list for unknown file version`() = runTest {
        // given
        coEvery { pathsLoaderVersionHelper.getVersionForParseFile(uri) } returns
            IPathsLoaderVersionHelper.ShareLoaderVersion.UNKNOWN

        // when
        val result = sut.getAllPaths(uri)

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAllPaths returns empty list when loader throws IOException`() = runTest {
        // given
        coEvery { pathsLoaderVersionHelper.getVersionForParseFile(uri) } returns
            IPathsLoaderVersionHelper.ShareLoaderVersion.V1
        coEvery { pathsLoaderRepositoryV1.loadAllRatingPathsFromFile(uri) } throws IOException()

        // when
        val result = sut.getAllPaths(uri)

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAllPaths maps each loaded path to an outer path info`() = runTest {
        // given
        stubSingleLoadedPath()

        // when
        val result = sut.getAllPaths(uri)

        // then
        assertEquals(1, result.size)
        assertEquals(99f, result[0].length)
        assertEquals(MostCommonRating.GOOD, result[0].mostCommonRating)
        assertTrue(result[0].isOuterPath)
    }

    @Test
    fun `getCachedOuterPath returns null for unknown id`() {
        // given

        // when
        val result = sut.getCachedOuterPath(404L)

        // then
        assertNull(result)
    }

    @Test
    fun `clearCachedOuterPaths empties the cache`() = runTest {
        // given
        stubSingleLoadedPath()
        sut.getAllPaths(uri)

        // when
        sut.clearCachedOuterPaths()

        // then
        assertTrue(sut.getCachedOuterPaths().isEmpty())
    }

    @Test
    fun `saveCachedPaths persists every cached path then clears the cache`() = runTest {
        // given
        stubSingleLoadedPath()
        sut.getAllPaths(uri)
        coEvery { pathRepository.createOuterNewPath(any(), any(), any(), any()) } returns 1L

        // when
        sut.saveCachedPaths()

        // then
        coVerify(exactly = 1) { pathRepository.createOuterNewPath(any(), any(), any(), any()) }
        assertTrue(sut.getCachedOuterPaths().isEmpty())
    }

    private fun stubSingleLoadedPath() {
        coEvery { pathsLoaderVersionHelper.getVersionForParseFile(uri) } returns
            IPathsLoaderVersionHelper.ShareLoaderVersion.V1
        coEvery { pathsLoaderRepositoryV1.loadAllRatingPathsFromFile(uri) } returns listOf(listOf(segment))
        every { pathDistancesRepository.calculateMostCommonPathRating(any()) } returns MostCommonRating.GOOD
        every { pathDistancesRepository.calculatePathLength(any<Array<MapPathSegment>>()) } returns 99f
    }
}
