package ru.lobotino.walktraveller.viewmodels

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
import ru.lobotino.walktraveller.model.map.MapCameraState
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserRotationRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.usecases.IUserLocationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IFinishPathWritingUseCase
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapStateInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRatingUseCase
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase
import ru.lobotino.walktraveller.usecases.interfaces.ITileSourceInteractor
import ru.lobotino.walktraveller.usecases.permissions.GeoPermissionsUseCase
import ru.lobotino.walktraveller.utils.IResourceManager

class MapViewModelCameraStateTest {

    private lateinit var mapStateInteractor: IMapStateInteractor
    private lateinit var sut: MapViewModel

    @Before
    fun setUp() {
        mapStateInteractor = mockk(relaxed = true)
        sut = MapViewModel(
            notificationsPermissionsUseCase = mockk<IPermissionsUseCase>(relaxed = true),
            geoPermissionsUseCase = mockk<GeoPermissionsUseCase>(relaxed = true),
            finishPathWritingUseCase = mockk<IFinishPathWritingUseCase>(relaxed = true),
            userLocationInteractor = mockk<IUserLocationInteractor>(relaxed = true),
            mapPathsInteractor = mockk<IMapPathsInteractor>(relaxed = true),
            mapStateInteractor = mapStateInteractor,
            tileSourceInteractor = mockk<ITileSourceInteractor>(relaxed = true),
            writingPathStatesRepository = mockk<IWritingPathStatesRepository>(relaxed = true),
            pathRatingUseCase = mockk<IPathRatingUseCase>(relaxed = true),
            userRotationRepository = mockk<IUserRotationRepository>(relaxed = true),
            userInfoRepository = mockk<IUserInfoRepository>(relaxed = true),
            resourceManager = mockk<IResourceManager>(relaxed = true),
            analyticsTracker = mockk<IAnalyticsTracker>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        // mockk state is per-instance; nothing global to clear
    }

    @Test
    fun `onCameraIdle saves the camera state via interactor`() {
        // given
        val center = MapPoint(12.0, 34.0)
        val zoom = 17.5

        // when
        sut.onCameraIdle(center, zoom)

        // then
        verify(exactly = 1) {
            mapStateInteractor.setLastCameraState(MapCameraState(center, zoom))
        }
    }
}
