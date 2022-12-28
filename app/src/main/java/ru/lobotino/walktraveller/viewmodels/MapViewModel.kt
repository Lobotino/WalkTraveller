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
import ru.lobotino.walktraveller.database.model.EntityPoint
import ru.lobotino.walktraveller.model.MapPath
import ru.lobotino.walktraveller.model.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IDefaultLocationRepository
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesStatesRepository
import ru.lobotino.walktraveller.ui.model.MapUiState
import ru.lobotino.walktraveller.ui.model.ShowPathsButtonState
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsInteractor
import ru.lobotino.walktraveller.utils.ext.toMapPoint

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private var updateCurrentSavedPath: Job? = null
    private var downloadAllPathsJob: Job? = null
    private var lastPaintedPoint: MapPoint? = null

    private var permissionsInteractor: IPermissionsInteractor? = null
    private lateinit var defaultLocationRepository: IDefaultLocationRepository
    private lateinit var locationUpdatesStatesRepository: ILocationUpdatesStatesRepository

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

    fun setLocationUpdatesStatesRepository(locationUpdatesStatesRepository: ILocationUpdatesStatesRepository) {
        this.locationUpdatesStatesRepository = locationUpdatesStatesRepository
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
        if (updateCurrentSavedPath == null || !updateCurrentSavedPath!!.isActive) {
            drawNewSegmentToPoint(MapPoint(location.latitude, location.longitude))
        }
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
        lastPaintedPoint = null
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

    fun updateNewPointsIfNeeded() {
        var needToUpdateAllPath = false
        if (locationUpdatesStatesRepository.isRequestingLocationUpdates() && !mapUiStateFlow.value.isWritePath) {
            mapUiStateFlow.update { uiState -> uiState.copy(isWritePath = true) }
            regularLocationUpdateStateFlow.tryEmit(true)
            needToUpdateAllPath = true
        }

        if (mapUiStateFlow.value.isWritePath) {
            updateCurrentSavedPath?.cancel()
            updateCurrentSavedPath = viewModelScope.launch {
                mapPathsInteractor.getLastSavedPath()?.let { lastSavedPath ->
                    mapUiStateFlow.update { uiState -> uiState.copy(needToClearMapNow = true) }
                    if (needToUpdateAllPath) {
                        lastPaintedPoint = lastSavedPath.pathPoints.first().toMapPoint()
                    }
                    drawUnpaintedYetPathSegments(lastSavedPath.pathPoints)
                }
            }
        }
    }

    private fun drawNewSegmentToPoint(newPoint: MapPoint) {
        if (lastPaintedPoint != null) {
            newPathSegmentFlow.tryEmit(
                Pair(
                    lastPaintedPoint!!,
                    newPoint
                )
            )
        }
        lastPaintedPoint = newPoint
    }

    private fun drawUnpaintedYetPathSegments(allPathPoints: List<EntityPoint>) {
        var needToDraw = false
        for (point in allPathPoints) {
            if (needToDraw) {
                drawNewSegmentToPoint(point.toMapPoint())
            } else {
                if (point.toMapPoint() == lastPaintedPoint) {
                    needToDraw = true
                }
            }
        }
    }
}
