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
import ru.lobotino.walktraveller.model.SegmentRating
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

class PathsMenuViewModelMapTapTest {

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
    fun `map-tap when no menu open emits nothing`() = runTest(testDispatcher) {
        // given: VM in initial state (no menu opened yet — currentBottomMenuState is null)
        val collected = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collected) }
        advanceUntilIdle()
        val snapshot = collected.size

        // when
        sut.onPathTappedOnMap(pathId = 42L)
        advanceUntilIdle()
        job.cancel()

        // then
        val after = collected.drop(snapshot)
        assertTrue(after.none { it is MapEvent.SetFocusedPath })
        assertTrue(after.none { it is MapEvent.ScrollListToPath })
    }

    @Test
    fun `map-tap on shown MY path with MY menu open emits SetFocusedPath then ScrollListToPath`() =
        runTest(testDispatcher) {
            // given
            givenShownRatingPathInOpenMyMenu(42L)
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when
            sut.onPathTappedOnMap(42L)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusEvent = after.filterIsInstance<MapEvent.SetFocusedPath>().single()
            val scrollEvent = after.filterIsInstance<MapEvent.ScrollListToPath>().single()
            assertEquals(42L, focusEvent.pathId)
            assertEquals(PathsMenuType.MY_PATHS, scrollEvent.pathsMenuType)
            assertEquals(42L, scrollEvent.pathId)
            assertTrue(
                "Expected SetFocusedPath before ScrollListToPath",
                after.indexOf(focusEvent) < after.indexOf(scrollEvent),
            )
            assertTrue(
                "Expected no FitCameraToBounds on map-tap",
                after.none { it is MapEvent.FitCameraToBounds },
            )
        }

    private fun ratingPath(pathId: Long): MapRatingPath = MapRatingPath(
        pathId = pathId,
        pathSegments = listOf(
            MapPathSegment(MapPoint(10.0, 20.0), MapPoint(30.0, 40.0), SegmentRating.GOOD),
        ),
    )

    /** Opens MY_PATHS_MENU and marks `pathId` as shown by routing through the
     *  standard show-button flow (same arrangement as PathsMenuViewModelFocusedPathTest). */
    private fun TestScope.givenShownRatingPathInOpenMyMenu(pathId: Long) {
        sut.onShowPathsMenuButtonClick()
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

    @Test
    fun `map-tap on already-focused path clears focus and does not scroll`() =
        runTest(testDispatcher) {
            // given: path 42 is focused via a prior map-tap
            givenShownRatingPathInOpenMyMenu(42L)
            sut.onPathTappedOnMap(42L)
            advanceUntilIdle()
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when: tap again on the same path
            sut.onPathTappedOnMap(42L)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusEvents = after.filterIsInstance<MapEvent.SetFocusedPath>()
            assertEquals(listOf<Long?>(null), focusEvents.map { it.pathId })
            assertTrue(
                "Expected no ScrollListToPath on toggle-off",
                after.none { it is MapEvent.ScrollListToPath },
            )
        }

    @Test
    fun `map-tap on second shown path swaps focus and scrolls to new`() =
        runTest(testDispatcher) {
            // given: path 42 focused, path 43 shown
            givenShownRatingPathInOpenMyMenu(42L)
            givenShownRatingPathInOpenMyMenu(43L)
            sut.onPathTappedOnMap(42L)
            advanceUntilIdle()
            val collected = mutableListOf<MapEvent>()
            val job = launch { sut.observeNewMapEvent.toList(collected) }
            advanceUntilIdle()
            val snapshot = collected.size

            // when
            sut.onPathTappedOnMap(43L)
            advanceUntilIdle()
            job.cancel()

            // then
            val after = collected.drop(snapshot)
            val focusEvents = after.filterIsInstance<MapEvent.SetFocusedPath>()
            assertEquals(listOf<Long?>(43L), focusEvents.map { it.pathId })
            val scrollEvents = after.filterIsInstance<MapEvent.ScrollListToPath>()
            assertEquals(1, scrollEvents.size)
            assertEquals(43L, scrollEvents.single().pathId)
        }
}
