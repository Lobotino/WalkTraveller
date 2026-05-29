package ru.lobotino.walktraveller.usecases

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.database.model.EntityMapPathSegment
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPoint
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.ICachePathsRepository
import ru.lobotino.walktraveller.repositories.interfaces.ILastCreatedPathIdRepository
import ru.lobotino.walktraveller.repositories.interfaces.IOptimizePathsSettingsRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor

@OptIn(ExperimentalCoroutinesApi::class)
class LocalMapPathsInteractorTest {

    private lateinit var databasePathRepository: IPathRepository
    private lateinit var cachePathRepository: ICachePathsRepository
    private lateinit var writingPathStatesRepository: IWritingPathStatesRepository
    private lateinit var lastCreatedPathIdRepository: ILastCreatedPathIdRepository
    private lateinit var pathRedactor: IPathRedactor
    private lateinit var optimizePathsSettingsRepository: IOptimizePathsSettingsRepository
    private lateinit var sut: LocalMapPathsInteractor

    @Before
    fun setUp() {
        databasePathRepository = mockk()
        cachePathRepository = mockk()
        writingPathStatesRepository = mockk()
        lastCreatedPathIdRepository = mockk()
        pathRedactor = mockk()
        optimizePathsSettingsRepository = mockk()
        every { optimizePathsSettingsRepository.getOptimizePathsApproximationDistance() } returns 1f
        sut = LocalMapPathsInteractor(
            databasePathRepository,
            cachePathRepository,
            UnconfinedTestDispatcher(),
            writingPathStatesRepository,
            lastCreatedPathIdRepository,
            pathRedactor,
            optimizePathsSettingsRepository
        )
    }

    @Test
    fun `getLastSavedRatingPath returns null when there is no last created path`() = runTest {
        // given
        every { lastCreatedPathIdRepository.getLastCreatedPathId() } returns null

        // when
        val result = sut.getLastSavedRatingPath()

        // then
        assertNull(result)
    }

    @Test
    fun `getSavedCommonPath returns cached path on cache hit`() = runTest {
        // given
        val cached = MapCommonPath(1L, MapPoint(1.0, 1.0), listOf(MapPoint(1.0, 1.0)))
        every { cachePathRepository.getCommonPath(any(), any()) } returns cached

        // when
        val result = sut.getSavedCommonPath(pathId = 1L, isOptimized = false)

        // then
        assertEquals(cached, result)
    }

    @Test
    fun `getSavedCommonPath returns null when no points stored`() = runTest {
        // given
        every { cachePathRepository.getCommonPath(any(), any()) } returns null
        coEvery { databasePathRepository.getAllPathPoints(1L) } returns emptyList()

        // when
        val result = sut.getSavedCommonPath(pathId = 1L, isOptimized = false)

        // then
        assertNull(result)
    }

    @Test
    fun `getSavedCommonPath builds path from stored points`() = runTest {
        // given
        every { cachePathRepository.getCommonPath(any(), any()) } returns null
        coEvery { databasePathRepository.getAllPathPoints(1L) } returns listOf(
            EntityPoint(1L, 1.0, 1.0, 0L),
            EntityPoint(2L, 2.0, 2.0, 0L)
        )
        every { cachePathRepository.saveCommonPath(any(), any()) } just Runs

        // when
        val result = sut.getSavedCommonPath(pathId = 1L, isOptimized = false)

        // then
        assertEquals(MapPoint(1.0, 1.0), result?.startPoint)
        assertEquals(2, result?.pathPoints?.size)
    }

    @Test
    fun `getSavedRatingPath returns null when no segments stored`() = runTest {
        // given
        every { cachePathRepository.getRatingPath(any(), any()) } returns null
        coEvery { databasePathRepository.getAllPathSegments(1L) } returns emptyList()

        // when
        val result = sut.getSavedRatingPath(pathId = 1L, withRatingOnly = false, isOptimized = false)

        // then
        assertNull(result)
    }

    @Test
    fun `getSavedRatingPath builds rating path from stored segments`() = runTest {
        // given
        every { cachePathRepository.getRatingPath(any(), any()) } returns null
        coEvery { databasePathRepository.getAllPathSegments(1L) } returns listOf(goodSegmentEntity())
        every { writingPathStatesRepository.isWritingPathNow() } returns false
        every { cachePathRepository.saveRatingPath(any(), any()) } just Runs

        // when
        val result = sut.getSavedRatingPath(pathId = 1L, withRatingOnly = false, isOptimized = false)

        // then
        assertEquals(1, result?.pathSegments?.size)
        assertEquals(SegmentRating.GOOD, result?.pathSegments?.first()?.rating)
    }

    @Test
    fun `getAllSavedPathsAsCommon returns a common path per stored path`() = runTest {
        // given
        coEvery { databasePathRepository.getAllPathsInfo() } returns listOf(storedPathEntity())
        every { cachePathRepository.getCommonPath(any(), any()) } returns null
        coEvery { databasePathRepository.getAllPathPoints(1L) } returns listOf(
            EntityPoint(1L, 1.0, 1.0, 0L),
            EntityPoint(2L, 2.0, 2.0, 0L)
        )
        every { cachePathRepository.saveCommonPath(any(), any()) } just Runs

        // when
        val result = sut.getAllSavedPathsAsCommon()

        // then
        assertEquals(1, result.size)
    }

    @Test
    fun `getAllSavedRatingPaths returns a rating path per stored path`() = runTest {
        // given
        coEvery { databasePathRepository.getAllPathsInfo() } returns listOf(storedPathEntity())
        every { cachePathRepository.getRatingPath(any(), any()) } returns null
        coEvery { databasePathRepository.getAllPathSegments(1L) } returns listOf(goodSegmentEntity())
        every { writingPathStatesRepository.isWritingPathNow() } returns false
        every { cachePathRepository.saveRatingPath(any(), any()) } just Runs

        // when
        val result = sut.getAllSavedRatingPaths(withRatingOnly = false)

        // then
        assertEquals(1, result.size)
    }

    @Test
    fun `getAllSavedPathsInfo returns complete cached info without recalculation`() = runTest {
        // given
        every { writingPathStatesRepository.isWritingPathNow() } returns false
        coEvery { databasePathRepository.getAllPathsInfo() } returns listOf(storedPathEntity())
        val cachedInfo = MapPathInfo(1L, 100L, MostCommonRating.GOOD, 5f, false)
        every { cachePathRepository.getMapPathInfo(1L) } returns cachedInfo

        // when
        val result = sut.getAllSavedPathsInfo()

        // then
        assertEquals(listOf(cachedInfo), result)
    }

    private fun storedPathEntity() = EntityPath(
        id = 1L,
        startPointId = 10L,
        length = 5f,
        mostCommonRating = MostCommonRating.GOOD.ordinal,
        isOuterPath = false
    )

    private fun goodSegmentEntity() = EntityMapPathSegment(
        startPoint = EntityPoint(1L, 1.0, 1.0, 0L),
        finishPoint = EntityPoint(2L, 2.0, 2.0, 0L),
        rating = SegmentRating.GOOD.ordinal,
        timestamp = 0L
    )
}
