package ru.lobotino.walktraveller.viewmodels

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathsSaverRepository
import ru.lobotino.walktraveller.ui.maplibre.pathBounds
import ru.lobotino.walktraveller.ui.model.MapEvent
import ru.lobotino.walktraveller.ui.model.PathItemButtonType
import ru.lobotino.walktraveller.ui.model.PathInfoItemShowButtonState
import ru.lobotino.walktraveller.ui.model.PathsMenuType
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IOuterPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase

class PathsMenuViewModelShortTapTest {

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
    fun `onPathInListShortTap hidden MY path outside select mode emits nothing`() = runTest(testDispatcher) {
        // given — путь не показывался

        // when
        val collectedEvents = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collectedEvents) }
        sut.onPathInListShortTap(pathId = 42L, pathsMenuType = PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        job.cancel()

        // then
        assertNull(collectedEvents.firstOrNull())
    }

    @Test
    fun `onPathInListShortTap hidden OUTER path outside select mode emits nothing`() = runTest(testDispatcher) {
        // given — путь не показывался

        // when
        val collectedEvents = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collectedEvents) }
        sut.onPathInListShortTap(pathId = 99L, pathsMenuType = PathsMenuType.OUTER_PATHS)
        advanceUntilIdle()
        job.cancel()

        // then
        assertNull(collectedEvents.firstOrNull())
    }

    @Test
    fun `onPathInListShortTap on shown MY path emits FitCameraToBounds with computed bounds`() = runTest(testDispatcher) {
        // given
        val pathId = 7L
        val path = ratingPath(pathId)
        val expectedBounds = pathBounds(path)!!
        coEvery {
            mapPathsInteractor.getSavedRatingPath(pathId, false, true)
        } returns path

        val collectedEvents = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collectedEvents) }

        // путь сначала показан через стандартную кнопку — это активирует markPathShown
        sut.onPathInListButtonClicked(
            pathId,
            PathItemButtonType.Show(PathInfoItemShowButtonState.DEFAULT),
            PathsMenuType.MY_PATHS,
        )
        advanceUntilIdle()

        // отбросить эмит ShowRatingPath, который пришёл от показа
        assertTrue("Expected ShowRatingPath as first event", collectedEvents.firstOrNull() is MapEvent.ShowRatingPath)

        // when
        sut.onPathInListShortTap(pathId, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        job.cancel()

        // then
        val fitEvent = collectedEvents.getOrNull(1)
        assertTrue("Expected FitCameraToBounds as second event, got: $fitEvent", fitEvent is MapEvent.FitCameraToBounds)
        assertEquals(expectedBounds, (fitEvent as MapEvent.FitCameraToBounds).bounds)
    }

    @Test
    fun `onPathInListShortTap on shown OUTER path emits FitCameraToBounds`() = runTest(testDispatcher) {
        // given
        val pathId = 11L
        val path = ratingPath(pathId)
        val expectedBounds = pathBounds(path)!!
        // getCachedOuterPath is called twice: once synchronously during show, once inside resolveShownPath coroutine
        coEvery { outerPathsInteractor.getCachedOuterPath(pathId) } returns path
        // Outer show is synchronous (no viewModelScope.launch), so markPathShown is called immediately;
        // start the collector AFTER the show to avoid needing to drain the ShowRatingPath event
        // (which was sent synchronously before any collector existed and was dropped by the rendezvous channel)
        sut.onPathInListButtonClicked(
            pathId,
            PathItemButtonType.Show(PathInfoItemShowButtonState.DEFAULT),
            PathsMenuType.OUTER_PATHS,
        )
        // path is now marked shown; start collecting before the short tap
        val collectedEvents = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collectedEvents) }

        // when
        sut.onPathInListShortTap(pathId, PathsMenuType.OUTER_PATHS)
        advanceUntilIdle()
        job.cancel()

        // then — FitCameraToBounds should be the first (and only) collected event
        val fitEvent = collectedEvents.firstOrNull()
        assertTrue("Expected FitCameraToBounds, got: $fitEvent", fitEvent is MapEvent.FitCameraToBounds)
        assertEquals(expectedBounds, (fitEvent as MapEvent.FitCameraToBounds).bounds)
    }

    @Test
    fun `onPathInListShortTap in MY select mode keeps existing toggle behavior`() = runTest(testDispatcher) {
        // given — long-tap включает select mode, выделяет элемент 5L
        sut.onPathInListLongTap(5L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()

        val collectedEvents = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collectedEvents) }

        // when — short tap другого элемента в select mode → должен ТОЛЬКО переключить выделение, без FitCamera
        sut.onPathInListShortTap(6L, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        job.cancel()

        // then — событие FitCamera не отправлено
        assertNull(collectedEvents.firstOrNull { it is MapEvent.FitCameraToBounds })
    }

    @Test
    fun `onPathInListShortTap on MY path that became hidden again emits nothing`() = runTest(testDispatcher) {
        // given — показываем, потом скрываем
        val pathId = 8L
        val path = ratingPath(pathId)
        coEvery {
            mapPathsInteractor.getSavedRatingPath(pathId, false, true)
        } returns path

        val collectedEvents = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collectedEvents) }

        sut.onPathInListButtonClicked(
            pathId,
            PathItemButtonType.Show(PathInfoItemShowButtonState.DEFAULT),
            PathsMenuType.MY_PATHS,
        )
        advanceUntilIdle()
        sut.onPathInListButtonClicked(
            pathId,
            PathItemButtonType.Show(PathInfoItemShowButtonState.HIDE),
            PathsMenuType.MY_PATHS,
        )
        advanceUntilIdle()

        val eventCountAfterShowHide = collectedEvents.size

        // when
        sut.onPathInListShortTap(pathId, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        job.cancel()

        // then — no additional events emitted after the show/hide sequence
        assertNull(
            "Expected no FitCameraToBounds after path hidden again",
            collectedEvents.drop(eventCountAfterShowHide).firstOrNull { it is MapEvent.FitCameraToBounds }
        )
    }

    @Test
    fun `onPathInListShortTap on shown MY path with empty segments emits nothing`() = runTest(testDispatcher) {
        // given — путь помечен «показан», но interactor возвращает пустой path → bounds == null
        val pathId = 12L
        val emptyPath = MapRatingPath(pathId = pathId, pathSegments = emptyList())
        coEvery {
            mapPathsInteractor.getSavedRatingPath(pathId, false, true)
        } returnsMany listOf(
            ratingPath(pathId), // при первом показе вернётся валидный path
            emptyPath,           // при resolveShownPath — пустой
        )

        val collectedEvents = mutableListOf<MapEvent>()
        val job = launch { sut.observeNewMapEvent.toList(collectedEvents) }

        sut.onPathInListButtonClicked(
            pathId,
            PathItemButtonType.Show(PathInfoItemShowButtonState.DEFAULT),
            PathsMenuType.MY_PATHS,
        )
        advanceUntilIdle()

        val eventCountAfterShow = collectedEvents.size

        // when
        sut.onPathInListShortTap(pathId, PathsMenuType.MY_PATHS)
        advanceUntilIdle()
        job.cancel()

        // then — no FitCameraToBounds emitted
        assertNull(
            "Expected no FitCameraToBounds when path has empty segments",
            collectedEvents.drop(eventCountAfterShow).firstOrNull { it is MapEvent.FitCameraToBounds }
        )
    }

    private fun ratingPath(pathId: Long): MapRatingPath = MapRatingPath(
        pathId = pathId,
        pathSegments = listOf(
            MapPathSegment(MapPoint(10.0, 20.0), MapPoint(30.0, 40.0), SegmentRating.GOOD),
        ),
    )
}
