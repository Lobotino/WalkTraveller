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
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.*
import ru.lobotino.walktraveller.repositories.interfaces.IDefaultLocationRepository
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesStatesRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRatingRepository
import ru.lobotino.walktraveller.ui.model.BottomMenuState
import ru.lobotino.walktraveller.ui.model.MapUiState
import ru.lobotino.walktraveller.ui.model.PathsInfoListState
import ru.lobotino.walktraveller.ui.model.ShowPathsButtonState
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsInteractor

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private var updateCurrentSavedPath: Job? = null
    private var downloadAllRatingPathsJob: Job? = null
    private var downloadAllPathsInfoJob: Job? = null
    private var lastPaintedPoint: MapPoint? = null

    private var geoPermissionsInteractor: IPermissionsInteractor? = null
    private var volumeKeysListenerPermissionsInteractor: IPermissionsInteractor? = null
    private lateinit var defaultLocationRepository: IDefaultLocationRepository
    private lateinit var locationUpdatesStatesRepository: ILocationUpdatesStatesRepository
    private lateinit var pathRatingRepository: IPathRatingRepository

    private lateinit var mapPathsInteractor: IMapPathsInteractor

    private val permissionsDeniedSharedFlow =
        MutableSharedFlow<List<String>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newPathSegmentFlow =
        MutableSharedFlow<MapPathSegment>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newCommonPathFlow =
        MutableSharedFlow<MapCommonPath>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newRatingPathFlow =
        MutableSharedFlow<MapRatingPath>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newPathsInfoListFlow =
        MutableSharedFlow<List<MapPathInfo>>(1, 0, BufferOverflow.DROP_OLDEST)

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
    val observeNewPathSegment: Flow<MapPathSegment> = newPathSegmentFlow
    val observeNewCommonPath: Flow<MapCommonPath> = newCommonPathFlow
    val observeNewRatingPath: Flow<MapRatingPath> = newRatingPathFlow
    val observeMapUiState: Flow<MapUiState> = mapUiStateFlow
    val observeRegularLocationUpdate: Flow<Boolean> = regularLocationUpdateStateFlow
    val observeNewPathsInfoList: Flow<List<MapPathInfo>> = newPathsInfoListFlow

    fun setGeoPermissionsInteractor(geoPermissionsInteractor: IPermissionsInteractor) {
        this.geoPermissionsInteractor = geoPermissionsInteractor
    }

    fun setVolumeKeysListenerPermissionsInteractor(volumeKeysListenerPermissionsInteractor: IPermissionsInteractor) {
        this.volumeKeysListenerPermissionsInteractor = volumeKeysListenerPermissionsInteractor
    }

    fun setMapPathInteractor(mapPathsInteractor: IMapPathsInteractor) {
        this.mapPathsInteractor = mapPathsInteractor
    }

    fun setDefaultLocationRepository(defaultLocationRepository: IDefaultLocationRepository) {
        this.defaultLocationRepository = defaultLocationRepository
    }

    fun setLocationUpdatesStatesRepository(locationUpdatesStatesRepository: ILocationUpdatesStatesRepository) {
        this.locationUpdatesStatesRepository = locationUpdatesStatesRepository
    }

    fun setPathRatingRepository(pathRatingRepository: IPathRatingRepository) {
        this.pathRatingRepository = pathRatingRepository
    }

    fun onInitFinish() {
        geoPermissionsInteractor?.requestPermissions(allGranted = {
            volumeKeysListenerPermissionsInteractor?.requestPermissions(someDenied = { deniedPermissions ->
                permissionsDeniedSharedFlow.tryEmit(deniedPermissions)
            })
        }, someDenied = { deniedPermissions ->
            permissionsDeniedSharedFlow.tryEmit(deniedPermissions)
        })

        mapUiStateFlow.update { uiState ->
            uiState.copy(mapCenter = defaultLocationRepository.getDefaultUserLocation())
        }
    }

    fun onGeoLocationUpdaterConnected() {
        geoPermissionsInteractor?.requestPermissions(someDenied = { deniedPermissions ->
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
            drawNewSegmentToPoint(
                MapPoint(location.latitude, location.longitude),
                pathRatingRepository.getCurrentRating()
            )
        }
    }

    fun onNewRatingReceive() {
        mapUiStateFlow.update { uiState ->
            uiState.copy(
                mapCenter = null,
                needToClearMapNow = false,
                newRating = pathRatingRepository.getCurrentRating(),
            )
        }
    }

    fun onStartPathButtonClicked() {
        regularLocationUpdateStateFlow.tryEmit(true)
        mapUiStateFlow.update { uiState ->
            uiState.copy(
                isWritePath = true,
                isPathFinished = false,
                needToClearMapNow = true,
                mapCenter = null,
                newRating = pathRatingRepository.getCurrentRating()
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
        if (downloadAllRatingPathsJob?.isActive == true || mapUiStateFlow.value.showPathsButtonState == ShowPathsButtonState.LOADING) {
            downloadAllRatingPathsJob?.cancel()
            downloadAllRatingPathsJob = null
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
            downloadAllRatingPathsJob = viewModelScope.launch {
                for (path in mapPathsInteractor.getAllSavedRatingPaths()) {
                    newRatingPathFlow.tryEmit(path)
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

    fun onRatingButtonClicked(ratingGiven: SegmentRating) {
        pathRatingRepository.setCurrentRating(ratingGiven)
        mapUiStateFlow.update { uiState ->
            uiState.copy(needToClearMapNow = false, newRating = ratingGiven)
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
                mapPathsInteractor.getLastSavedRatingPath()?.let { lastSavedPath ->
                    mapUiStateFlow.update { uiState -> uiState.copy(needToClearMapNow = true) }
                    if (needToUpdateAllPath) {
                        lastPaintedPoint =
                            lastSavedPath.pathSegments.first().startPoint
                    }
                    drawUnpaintedYetPathSegments(lastSavedPath.pathSegments)
                }
            }
        }
    }

    private fun drawNewSegmentToPoint(newPoint: MapPoint, segmentRating: SegmentRating) {
        if (lastPaintedPoint != null) {
            newPathSegmentFlow.tryEmit(
                MapPathSegment(lastPaintedPoint!!, newPoint, segmentRating)
            )
        }
        lastPaintedPoint = newPoint
    }

    private fun drawUnpaintedYetPathSegments(allPathSegments: List<MapPathSegment>) {
        var needToDraw = false
        for (segment in allPathSegments) {
            if (needToDraw) {
                newPathSegmentFlow.tryEmit(segment)
            } else {
                if (segment.finishPoint == lastPaintedPoint) {
                    needToDraw = true
                }
            }
        }
    }

    fun onShowPathsMenuClicked() {
        mapUiStateFlow.update { uiState ->
            uiState.copy(
                needToClearMapNow = false,
                bottomMenuState = BottomMenuState.PATHS_MENU
            )
        }

        downloadAllRatingPathsJob?.cancel()
        downloadAllPathsInfoJob = viewModelScope.launch {
            mapUiStateFlow.update { uiState ->
                uiState.copy(pathsInfoListState = PathsInfoListState.LOADING)
            }
            newPathsInfoListFlow.tryEmit(mapPathsInteractor.getAllSavedPathsInfo())
            mapUiStateFlow.update { uiState ->
                uiState.copy(pathsInfoListState = PathsInfoListState.DEFAULT)
            }
        }
    }

    fun onHidePathsMenuClicked() {
        downloadAllRatingPathsJob?.cancel()
        mapUiStateFlow.update { uiState ->
            uiState.copy(
                needToClearMapNow = false,
                bottomMenuState = BottomMenuState.DEFAULT
            )
        }
    }
}
