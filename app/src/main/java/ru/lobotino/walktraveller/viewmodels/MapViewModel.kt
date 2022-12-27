package ru.lobotino.walktraveller.viewmodels

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lobotino.walktraveller.model.MapPath
import ru.lobotino.walktraveller.model.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IDefaultLocationRepository
import ru.lobotino.walktraveller.ui.model.MapUiState
import ru.lobotino.walktraveller.ui.model.ShowPathsButtonState
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsInteractor

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private var downloadAllPathsJob: Job? = null
    private var lastPoint: MapPoint? = null

    private var permissionsInteractor: IPermissionsInteractor? = null
    private lateinit var defaultLocationRepository: IDefaultLocationRepository

    private lateinit var mapPathsInteractor: IMapPathsInteractor

    private val permissionsDeniedSharedFlow =
        MutableSharedFlow<List<String>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newPathSegmentFlow =
        MutableSharedFlow<Pair<MapPoint, MapPoint>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newPathFlow = MutableSharedFlow<MapPath>(1, 0, BufferOverflow.DROP_OLDEST)

    private val regularLocationUpdateStateFlow = MutableStateFlow(false)

    private val mapUiStateFlow =
        MutableStateFlow(
            MapUiState(
                isWritePath = false,
                isPathFinished = false,
                needToClearMapNow = false,
                mapCenter = null,
                showPathsButtonState = ShowPathsButtonState.DEFAULT
            )
        )

    val observePermissionsDeniedResult: Flow<List<String>> = permissionsDeniedSharedFlow
    val observeNewPathSegment: Flow<Pair<MapPoint, MapPoint>> = newPathSegmentFlow
    val observeNewPath: Flow<MapPath> = newPathFlow
    val observeMapUiState: Flow<MapUiState> = mapUiStateFlow
    val observeRegularLocationUpdate: Flow<Boolean> = regularLocationUpdateStateFlow

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

        mapUiStateFlow.update { uiState ->
            uiState.copy(mapCenter = defaultLocationRepository.getDefaultUserLocation())
        }
    }

    fun onGeoLocationUpdaterConnected() {
        permissionsInteractor?.requestGeoPermissions(someDenied = { deniedPermissions ->
            permissionsDeniedSharedFlow.tryEmit(deniedPermissions)
        })
    }

    fun onGeoLocationUpdaterDisconnected() {
        mapUiStateFlow.update { uiState ->
            uiState.copy(
                isWritePath = false,
                isPathFinished = false,
                mapCenter = null
            )
        }
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
        regularLocationUpdateStateFlow.tryEmit(true)
        mapUiStateFlow.update { uiState ->
            uiState.copy(
                isWritePath = true,
                isPathFinished = false,
                needToClearMapNow = true,
                mapCenter = null
            )
        }
    }

    fun onStopPathButtonClicked() {
        regularLocationUpdateStateFlow.tryEmit(false)
        mapUiStateFlow.update { uiState ->
            uiState.copy(
                isWritePath = false,
                isPathFinished = true,
                needToClearMapNow = false,
                mapCenter = null
            )
        }
        lastPoint = null
    }

    fun onShowAllPathsButtonClicked() {
        if (downloadAllPathsJob?.isActive == true || mapUiStateFlow.value.showPathsButtonState == ShowPathsButtonState.LOADING) {
            downloadAllPathsJob?.cancel()
            downloadAllPathsJob = null
            mapUiStateFlow.update { uiState ->
                uiState.copy(
                    needToClearMapNow = false,
                    mapCenter = null,
                    showPathsButtonState = ShowPathsButtonState.DEFAULT
                )
            }
        } else {
            mapUiStateFlow.update { uiState ->
                uiState.copy(
                    needToClearMapNow = true,
                    mapCenter = null,
                    showPathsButtonState = ShowPathsButtonState.LOADING
                )
            }
            downloadAllPathsJob = viewModelScope.launch {
                for (path in mapPathsInteractor.getAllSavedPaths()) {
                    newPathFlow.tryEmit(path)
                }
                mapUiStateFlow.update { uiState ->
                    uiState.copy(
                        needToClearMapNow = false,
                        mapCenter = null,
                        showPathsButtonState = ShowPathsButtonState.DEFAULT
                    )
                }
            }
        }
    }
}
