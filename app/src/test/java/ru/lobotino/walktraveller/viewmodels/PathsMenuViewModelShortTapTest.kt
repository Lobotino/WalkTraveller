package ru.lobotino.walktraveller.viewmodels

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathsSaverRepository
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
        // given — путь не показывался, state по умолчанию

        // when
        sut.onPathInListShortTap(pathId = 42L, pathsMenuType = PathsMenuType.MY_PATHS)
        advanceUntilIdle()

        // then — канал событий пуст
        val emittedOrNull = withTimeoutOrNull(50) { sut.observeNewMapEvent.first() }
        assertNull(emittedOrNull)
    }

    // Хелпер: пытаемся забрать первый эмит с коротким таймаутом-эквивалентом
    private suspend fun <T> withTimeoutOrNullFirst(
        flow: kotlinx.coroutines.flow.Flow<T>,
    ): T? = try {
        withTimeoutOrNull(50) { flow.first() }
    } catch (e: Throwable) {
        null
    }

    private fun ratingPath(pathId: Long): MapRatingPath = MapRatingPath(
        pathId = pathId,
        pathSegments = listOf(
            MapPathSegment(MapPoint(10.0, 20.0), MapPoint(30.0, 40.0), SegmentRating.GOOD),
        ),
    )
}
