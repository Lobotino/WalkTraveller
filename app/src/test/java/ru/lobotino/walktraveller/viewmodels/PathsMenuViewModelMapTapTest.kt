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
}
