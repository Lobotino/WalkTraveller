package ru.lobotino.walktraveller.viewmodels

import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserRotationRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.ui.maplibre.PathBounds
import ru.lobotino.walktraveller.usecases.IUserLocationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IFinishPathWritingUseCase
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapStateInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRatingUseCase
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase
import ru.lobotino.walktraveller.usecases.interfaces.ITileSourceInteractor
import ru.lobotino.walktraveller.usecases.permissions.GeoPermissionsUseCase
import ru.lobotino.walktraveller.utils.IResourceManager

class MapViewModelFitCameraTest {

    private lateinit var sut: MapViewModel

    @Before
    fun setUp() {
        sut = MapViewModel(
            notificationsPermissionsUseCase = mockk<IPermissionsUseCase>(relaxed = true),
            geoPermissionsUseCase = mockk<GeoPermissionsUseCase>(relaxed = true),
            finishPathWritingUseCase = mockk<IFinishPathWritingUseCase>(relaxed = true),
            userLocationInteractor = mockk<IUserLocationInteractor>(relaxed = true),
            mapPathsInteractor = mockk<IMapPathsInteractor>(relaxed = true),
            mapStateInteractor = mockk<IMapStateInteractor>(relaxed = true),
            tileSourceInteractor = mockk<ITileSourceInteractor>(relaxed = true),
            writingPathStatesRepository = mockk<IWritingPathStatesRepository>(relaxed = true),
            pathRatingUseCase = mockk<IPathRatingUseCase>(relaxed = true),
            userRotationRepository = mockk<IUserRotationRepository>(relaxed = true),
            userInfoRepository = mockk<IUserInfoRepository>(relaxed = true),
            resourceManager = mockk<IResourceManager>(relaxed = true),
            analyticsTracker = mockk<IAnalyticsTracker>(relaxed = true),
        )
    }

    @Test
    fun `fitCameraToBounds emits the same bounds to observeFitCameraToBounds`() = runTest {
        // given
        val bounds = PathBounds(minLat = 1.0, maxLat = 2.0, minLng = 3.0, maxLng = 4.0)

        // when
        sut.fitCameraToBounds(bounds)
        val emitted = sut.observeFitCameraToBounds.first()

        // then
        assertEquals(bounds, emitted)
    }
}
