package ru.lobotino.walktraveller.viewmodels

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathsSaverRepository
import ru.lobotino.walktraveller.ui.model.MapEvent
import ru.lobotino.walktraveller.ui.model.PathInfoItemShowButtonState
import ru.lobotino.walktraveller.ui.model.PathItemButtonType
import ru.lobotino.walktraveller.ui.model.PathsMenuType
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IOuterPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase

class PathsMenuViewModelFocusedPathTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var pathsSaverRepository: IPathsSaverRepository
    private lateinit var externalStoragePermissionsUseCase: IPermissionsUseCase
    private lateinit var mapPathsInteractor: IMapPathsInteractor
    private lateinit var outerPathsInteractor: IOuterPathsInteractor
    private lateinit var pathRedactor: IPathRedactor
    private lateinit var analyticsTracker: IAnalyticsTracker
    private lateinit var sut: PathsMenuViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pathsSaverRepository = mockk(relaxed = true)
        externalStoragePermissionsUseCase = mockk(relaxed = true)
        mapPathsInteractor = mockk(relaxed = true)
        outerPathsInteractor = mockk(relaxed = true)
        pathRedactor = mockk(relaxed = true)
        analyticsTracker = mockk(relaxed = true)
        sut = PathsMenuViewModel(
            pathsSaverRepository,
            externalStoragePermissionsUseCase,
            mapPathsInteractor,
            outerPathsInteractor,
            pathRedactor,
            analyticsTracker,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `short-tap on shown non-focused path emits SetFocusedPath then FitCameraToBounds`() =
        runTest(testDispatcher) {
            // given: a shown path with id=42 in MY_PATHS, no current focus
            givenShownRatingPath(pathId = 42L)
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when
            sut.onPathInListShortTap(pathId = 42L, pathsMenuType = PathsMenuType.MY_PATHS)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusEvent = after.filterIsInstance<MapEvent.SetFocusedPath>().single()
            val fitEvent = after.filterIsInstance<MapEvent.FitCameraToBounds>().single()
            assertEquals(42L, focusEvent.pathId)
            assertTrue(
                "Expected SetFocusedPath before FitCameraToBounds",
                after.indexOf(focusEvent) < after.indexOf(fitEvent),
            )
        }

    @Test
    fun `tap on already-focused path clears focus and does not move camera`() =
        runTest(testDispatcher) {
            // given
            givenShownRatingPath(42L)
            sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS) // first tap → focuses
            advanceUntilIdle()
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when: second tap on the same path
            sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusEvents = after.filterIsInstance<MapEvent.SetFocusedPath>()
            assertEquals(listOf<Long?>(null), focusEvents.map { it.pathId })
            assertTrue(after.none { it is MapEvent.FitCameraToBounds })
        }

    @Test
    fun `tap on second shown path swaps focus and fits camera to new one`() =
        runTest(testDispatcher) {
            // given
            givenShownRatingPath(42L)
            givenShownRatingPath(43L)
            sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
            advanceUntilIdle()
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when
            sut.onPathInListShortTap(43L, PathsMenuType.MY_PATHS)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusEvents = after.filterIsInstance<MapEvent.SetFocusedPath>()
            assertEquals(listOf<Long?>(43L), focusEvents.map { it.pathId })
            val fitEvents = after.filterIsInstance<MapEvent.FitCameraToBounds>()
            assertEquals(1, fitEvents.size)
        }

    @Test
    fun `tap on hidden path leaves focus unchanged and emits nothing`() =
        runTest(testDispatcher) {
            // given a hidden path (no givenShownRatingPath call)
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when
            sut.onPathInListShortTap(99L, PathsMenuType.MY_PATHS)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            assertTrue(after.none { it is MapEvent.SetFocusedPath })
            assertTrue(after.none { it is MapEvent.FitCameraToBounds })
        }

    @Test
    fun `long-tap entering multi-select clears focus and emits SetFocusedPath null`() =
        runTest(testDispatcher) {
            // given: a focused path
            givenShownRatingPath(42L)
            sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
            advanceUntilIdle()
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when: long-tap on the same (or any) path enters select mode
            sut.onPathInListLongTap(42L, PathsMenuType.MY_PATHS)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusEvents = after.filterIsInstance<MapEvent.SetFocusedPath>()
            assertEquals(listOf<Long?>(null), focusEvents.map { it.pathId })
        }

    @Test
    fun `hiding the focused path emits SetFocusedPath null`() =
        runTest(testDispatcher) {
            // given: a focused path in MY_PATHS
            givenShownRatingPath(42L)
            sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
            advanceUntilIdle()
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when: hide that path via its show button (LOADING/HIDE branch)
            sut.onPathInListButtonClicked(
                42L,
                PathItemButtonType.Show(PathInfoItemShowButtonState.HIDE),
                PathsMenuType.MY_PATHS,
            )
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusEvents = after.filterIsInstance<MapEvent.SetFocusedPath>()
            assertEquals(listOf<Long?>(null), focusEvents.map { it.pathId })
        }

    @Test
    fun `hiding a non-focused path does not emit SetFocusedPath`() =
        runTest(testDispatcher) {
            // given: path 42 is focused, path 43 is shown but not focused
            givenShownRatingPath(42L)
            givenShownRatingPath(43L)
            sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
            advanceUntilIdle()
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when: hide the non-focused path 43
            sut.onPathInListButtonClicked(
                43L,
                PathItemButtonType.Show(PathInfoItemShowButtonState.HIDE),
                PathsMenuType.MY_PATHS,
            )
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            assertTrue(after.none { it is MapEvent.SetFocusedPath })
        }

    @Test
    fun `deleting the focused path emits SetFocusedPath null`() =
        runTest(testDispatcher) {
            // given: path 42 is focused in MY_PATHS
            givenShownRatingPath(42L)
            sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
            advanceUntilIdle()
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when: confirm deletion of the focused path
            sut.onConfirmMyPathDelete(42L)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusEvents = after.filterIsInstance<MapEvent.SetFocusedPath>()
            assertEquals(listOf<Long?>(null), focusEvents.map { it.pathId })
        }

    @Test
    fun `ClearMap emit for MY_PATHS also clears focus`() =
        runTest(testDispatcher) {
            // given: a focused path in MY_PATHS and the show-paths button in DEFAULT state
            givenShownRatingPath(42L)
            sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
            advanceUntilIdle()
            // put showPathsButtonState into DEFAULT so the ClearMap branch is reachable
            coEvery { mapPathsInteractor.getAllSavedPathsInfo() } returns listOf(
                MapPathInfo(
                    pathId = 42L,
                    timestamp = 0L,
                    mostCommonRating = MostCommonRating.NONE,
                    length = 0f,
                    isOuterPath = false,
                ),
            )
            sut.onShowPathsMenuButtonClick()
            advanceUntilIdle()
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when: trigger ClearMap for MY_PATHS via show-selected-paths with no selection
            sut.onShowSelectedPathsButtonClicked(PathsMenuType.MY_PATHS)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusEvents = after.filterIsInstance<MapEvent.SetFocusedPath>()
            assertEquals(listOf<Long?>(null), focusEvents.map { it.pathId })
            assertTrue(after.any { it is MapEvent.ClearMap })
        }

    @Test
    fun `focus in MY_PATHS does not affect OUTER_PATHS focus state`() =
        runTest(testDispatcher) {
            // given
            givenShownRatingPath(42L)              // MY_PATHS
            givenShownOuterPath(7L)                // OUTER_PATHS
            sut.onPathInListShortTap(42L, PathsMenuType.MY_PATHS)
            advanceUntilIdle()
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when
            sut.onPathInListShortTap(7L, PathsMenuType.OUTER_PATHS)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusIds = after.filterIsInstance<MapEvent.SetFocusedPath>()
                .map { it.pathId }
            assertEquals(listOf<Long?>(7L), focusIds) // only OUTER focus event
        }

    /**
     * Marks pathId as shown in MY_PATHS by routing through the standard show button flow,
     * mirroring the arrangement used in PathsMenuViewModelShortTapTest.
     */
    private fun TestScope.givenShownRatingPath(pathId: Long) {
        val path = ratingPath(pathId)
        coEvery {
            mapPathsInteractor.getSavedRatingPath(pathId, false, true)
        } returns path
        sut.onPathInListButtonClicked(
            pathId,
            PathItemButtonType.Show(PathInfoItemShowButtonState.DEFAULT),
            PathsMenuType.MY_PATHS,
        )
        advanceUntilIdle()
    }

    /**
     * Marks pathId as shown in OUTER_PATHS by routing through the standard show button flow.
     */
    private fun givenShownOuterPath(pathId: Long) {
        val path = ratingPath(pathId)
        every { outerPathsInteractor.getCachedOuterPath(pathId) } returns path
        sut.onPathInListButtonClicked(
            pathId,
            PathItemButtonType.Show(PathInfoItemShowButtonState.DEFAULT),
            PathsMenuType.OUTER_PATHS,
        )
    }

    private fun ratingPath(pathId: Long): MapRatingPath = MapRatingPath(
        pathId = pathId,
        pathSegments = listOf(
            MapPathSegment(MapPoint(10.0, 20.0), MapPoint(30.0, 40.0), SegmentRating.GOOD),
        ),
    )
}
