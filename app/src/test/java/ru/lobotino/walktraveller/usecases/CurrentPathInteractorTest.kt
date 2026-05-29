package ru.lobotino.walktraveller.usecases

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.usecases.interfaces.IPathRatingUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentPathInteractorTest {

    private val point = MapPoint(1.0, 2.0)

    private lateinit var databasePathRepository: IPathRepository
    private lateinit var pathRatingUseCase: IPathRatingUseCase
    private lateinit var sut: CurrentPathInteractor

    @Before
    fun setUp() {
        databasePathRepository = mockk()
        pathRatingUseCase = mockk()
        coEvery { databasePathRepository.createNewPath(any(), any(), any(), any(), any()) } returns NEW_PATH_ID
        coEvery { databasePathRepository.addNewPathPoint(any(), any(), any(), any()) } returns 0L
        every { pathRatingUseCase.getCurrentRating() } returns SegmentRating.NORMAL
        sut = CurrentPathInteractor(
            databasePathRepository,
            pathRatingUseCase,
            UnconfinedTestDispatcher()
        )
    }

    @Test
    fun `first point creates a new path`() = runTest {
        // given

        // when
        sut.addNewPathPoint(point)

        // then
        coVerify(exactly = 1) { databasePathRepository.createNewPath(point, false, any(), any(), any()) }
        coVerify(exactly = 0) { databasePathRepository.addNewPathPoint(any(), any(), any(), any()) }
    }

    @Test
    fun `second point is appended to the created path with current rating`() = runTest {
        // given
        sut.addNewPathPoint(point)

        // when
        sut.addNewPathPoint(point)

        // then
        coVerify(exactly = 1) { databasePathRepository.createNewPath(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) {
            databasePathRepository.addNewPathPoint(NEW_PATH_ID, point, SegmentRating.NORMAL, any())
        }
    }

    @Test
    fun `finishing the path makes the next point start a new path`() = runTest {
        // given
        sut.addNewPathPoint(point)
        sut.finishCurrentPath()

        // when
        sut.addNewPathPoint(point)

        // then
        coVerify(exactly = 2) { databasePathRepository.createNewPath(any(), any(), any(), any(), any()) }
    }

    private companion object {
        const val NEW_PATH_ID = 42L
    }
}
