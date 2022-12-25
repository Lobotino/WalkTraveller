package ru.lobotino.walktraveller.viewmodels

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ru.lobotino.walktraveller.model.MapPath
import ru.lobotino.walktraveller.model.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IDefaultLocationRepository
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsInteractor

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private var lastPoint: MapPoint? = null

    private var permissionsInteractor: IPermissionsInteractor? = null
    private lateinit var defaultLocationRepository: IDefaultLocationRepository

    private lateinit var mapPathsInteractor: IMapPathsInteractor

    private val permissionsDeniedSharedFlow =
        MutableSharedFlow<List<String>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newPathSegmentFlow =
        MutableSharedFlow<Pair<MapPoint, MapPoint>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newPathFlow = MutableSharedFlow<MapPath>(1, 0, BufferOverflow.DROP_OLDEST)
    private val mapCenterUpdateFlow =
        MutableSharedFlow<MapPoint>(1, 0, BufferOverflow.DROP_OLDEST)
    private val regularLocationUpdateStateFlow = MutableStateFlow(false)
    private val writePathState = MutableStateFlow(false)

    val observePermissionsDeniedResult: Flow<List<String>> = permissionsDeniedSharedFlow
    val observeNewPathSegment: Flow<Pair<MapPoint, MapPoint>> = newPathSegmentFlow
    val observeNewPath: Flow<MapPath> = newPathFlow
    val observeMapCenterUpdate: Flow<MapPoint> = mapCenterUpdateFlow
    val observeRegularLocationUpdateState: Flow<Boolean> = regularLocationUpdateStateFlow
    val observeWritePathState: Flow<Boolean> = writePathState

    fun setPermissionsInteractor(permissionsInteractor: IPermissionsInteractor) {
        this.permissionsInteractor = permissionsInteractor
    }

    fun setDefaultLocationRepository(defaultLocationRepository: IDefaultLocationRepository) {
        this.defaultLocationRepository = defaultLocationRepository
    }

    fun setMapPathInteractor(mapPathsInteractor: IMapPathsInteractor) {
        this.mapPathsInteractor = mapPathsInteractor
    }

    fun onInitFinish() {
        permissionsInteractor?.requestGeoPermissions(someDenied = { deniedPermissions ->
            permissionsDeniedSharedFlow.tryEmit(deniedPermissions)
        })

        mapCenterUpdateFlow.tryEmit(defaultLocationRepository.getDefaultUserLocation())
    }

    fun onGeoLocationUpdaterConnected() {
        permissionsInteractor?.requestGeoPermissions(someDenied = { deniedPermissions ->
            permissionsDeniedSharedFlow.tryEmit(deniedPermissions)
        })
    }

    fun onGeoLocationUpdaterDisconnected() {
        regularLocationUpdateStateFlow.tryEmit(false)
    }

    fun onNewLocationReceive(location: Location) {
        val newPoint = MapPoint(location.latitude, location.longitude)
        if (lastPoint != null) {
            newPathSegmentFlow.tryEmit(
                Pair(
                    lastPoint!!,
                    newPoint
                )
            )
        }
        lastPoint = newPoint
    }

    fun onStartPathButtonClicked() {
        writePathState.tryEmit(true)
        regularLocationUpdateStateFlow.tryEmit(true)
    }

    fun onStopPathButtonClicked() {
        writePathState.tryEmit(false)
        regularLocationUpdateStateFlow.tryEmit(false)
        lastPoint = null
    }

    fun onShowAllPathsButtonClicked() {
        viewModelScope.launch {
            for (path in mapPathsInteractor.getAllSavedPaths()) {
                newPathFlow.tryEmit(path)
            }
        }
    }
}
