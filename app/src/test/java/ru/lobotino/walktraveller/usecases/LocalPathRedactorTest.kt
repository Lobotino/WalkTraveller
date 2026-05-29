package ru.lobotino.walktraveller.usecases

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathDistancesRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository

@OptIn(ExperimentalCoroutinesApi::class)
class LocalPathRedactorTest {

    private lateinit var databasePathRepository: IPathRepository
    private lateinit var pathDistancesRepository: IPathDistancesRepository
    private lateinit var sut: LocalPathRedactor

    @Before
    fun setUp() {
        databasePathRepository = mockk()
        pathDistancesRepository = mockk()
        sut = LocalPathRedactor(
            databasePathRepository,
            pathDistancesRepository,
            UnconfinedTestDispatcher()
        )
    }

    @Test
    fun `deletePath delegates to repository`() = runTest {
        // given
        coEvery { databasePathRepository.deletePath(any()) } just Runs

        // when
        sut.deletePath(5L)

        // then
        coVerify(exactly = 1) { databasePathRepository.deletePath(5L) }
    }

    @Test
    fun `deletePath leaves no path in the deleting set after completion`() = runTest {
        // given
        coEvery { databasePathRepository.deletePath(any()) } just Runs

        // when
        sut.deletePath(5L)

        // then
        assertTrue(sut.getDeletingNowPathsIds().isEmpty())
    }

    @Test
    fun `deletePaths delegates to repository`() = runTest {
        // given
        val pathIds = listOf(1L, 2L, 3L)
        coEvery { databasePathRepository.deletePaths(any()) } just Runs

        // when
        sut.deletePaths(pathIds)

        // then
        coVerify(exactly = 1) { databasePathRepository.deletePaths(pathIds) }
    }

    @Test
    fun `updatePathLength persists and returns calculated length`() = runTest {
        // given
        val path = MapCommonPath(1L, MapPoint(0.0, 0.0), listOf(MapPoint(0.0, 0.0)))
        every { pathDistancesRepository.calculatePathLength(any<Array<MapPoint>>()) } returns 123f
        coEvery { databasePathRepository.updatePathLength(any(), any()) } just Runs

        // when
        val result = sut.updatePathLength(path)

        // then
        assertEquals(123f, result)
        coVerify(exactly = 1) { databasePathRepository.updatePathLength(1L, 123f) }
    }

    @Test
    fun `updatePathMostCommonRating persists and returns calculated rating`() = runTest {
        // given
        val path = MapRatingPath(1L, emptyList())
        every { pathDistancesRepository.calculateMostCommonPathRating(any()) } returns MostCommonRating.GOOD
        coEvery { databasePathRepository.updatePathMostCommonRating(any(), any()) } just Runs

        // when
        val result = sut.updatePathMostCommonRating(path)

        // then
        assertEquals(MostCommonRating.GOOD, result)
        coVerify(exactly = 1) {
            databasePathRepository.updatePathMostCommonRating(1L, MostCommonRating.GOOD)
        }
    }
}
