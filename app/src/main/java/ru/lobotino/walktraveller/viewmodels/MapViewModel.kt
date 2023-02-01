package ru.lobotino.walktraveller.viewmodels

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.*
import ru.lobotino.walktraveller.repositories.interfaces.IDefaultLocationRepository
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesStatesRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRatingRepository
import ru.lobotino.walktraveller.ui.PathsInfoAdapter
import ru.lobotino.walktraveller.ui.PathsInfoAdapter.PathItemButtonType.SHOW
import ru.lobotino.walktraveller.ui.model.*
import ru.lobotino.walktraveller.usecases.IUserLocationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsInteractor

class MapViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private val TAG = MapViewModel::class.java.canonicalName
    }

    private var updateCurrentSavedPath: Job? = null
    private var downloadRatingPathsJob: Job? = null
    private var downloadAllPathsInfoJob: Job? = null
    private var backgroundCachingRatingPathsJob: Job? = null
    private var backgroundCachingCommonPathsJob: Job? = null
    private var backgroundCachingPathsInfoJob: Job? = null
    private var lastPaintedPoint: MapPoint? = null

    private var geoPermissionsInteractor: IPermissionsInteractor? = null
    private var volumeKeysListenerPermissionsInteractor: IPermissionsInteractor? = null
    private lateinit var defaultLocationRepository: IDefaultLocationRepository
    private lateinit var locationUpdatesStatesRepository: ILocationUpdatesStatesRepository
    private lateinit var pathRatingRepository: IPathRatingRepository
    private lateinit var userLocationInteractor: IUserLocationInteractor

    private lateinit var mapPathsInteractor: IMapPathsInteractor

    private var showedPathIdsList: MutableList<Long> = ArrayList()

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
    private val newMapCenterFlow =
        MutableSharedFlow<MapPoint>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newPathInfoListItemStateFlow =
        MutableSharedFlow<Pair<Long, PathInfoItemShowButtonState>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val hidePathFlow =
        MutableSharedFlow<Long>(1, 0, BufferOverflow.DROP_OLDEST)

    private val regularLocationUpdateStateFlow = MutableStateFlow(false)

    private var clearMapNowListener: (() -> Unit)? = null

    private val mapUiStateFlow =
        MutableStateFlow(
            MapUiState(
                isWritePath = false,
                isPathFinished = false
            )
        )

    val observePermissionsDeniedResult: Flow<List<String>> = permissionsDeniedSharedFlow
    val observeNewPathSegment: Flow<MapPathSegment> = newPathSegmentFlow
    val observeNewCommonPath: Flow<MapCommonPath> = newCommonPathFlow
    val observeNewRatingPath: Flow<MapRatingPath> = newRatingPathFlow
    val observeMapUiState: Flow<MapUiState> = mapUiStateFlow
    val observeRegularLocationUpdate: Flow<Boolean> = regularLocationUpdateStateFlow
    val observeNewPathsInfoList: Flow<List<MapPathInfo>> = newPathsInfoListFlow
    val observeNewMapCenter: Flow<MapPoint> = newMapCenterFlow
    val observeNewPathInfoListItemState: Flow<Pair<Long, PathInfoItemShowButtonState>> =
        newPathInfoListItemStateFlow
    val observeHidePath: Flow<Long> = hidePathFlow

    fun observeNewUserLocation() : Flow<MapPoint> = userLocationInteractor.observeCurrentUserLocation()

    fun observeNeedToClearMapNow(listener: (() -> Unit)?) {
        clearMapNowListener = listener
    }

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

    fun setUserLocationInteractor(userLocationInteractor: IUserLocationInteractor) {
        this.userLocationInteractor = userLocationInteractor
    }

    fun onInitFinish() {
        startBackgroundCachingPaths()

        geoPermissionsInteractor?.requestPermissions(allGranted = {
            volumeKeysListenerPermissionsInteractor?.requestPermissions(someDenied = { deniedPermissions ->
                permissionsDeniedSharedFlow.tryEmit(deniedPermissions)
            })
        }, someDenied = { deniedPermissions ->
            permissionsDeniedSharedFlow.tryEmit(deniedPermissions)
        })

        userLocationInteractor.apply {
            observeCurrentUserLocation().onEach { newUserLocation ->
                //TODO
            }.launchIn(viewModelScope)

            observeUserLocationErrors().onEach { newUserLocationError ->
                Log.w(TAG, newUserLocationError)
            }.launchIn(viewModelScope)

            getCurrentUserLocation { location ->
                newMapCenterFlow.tryEmit(location)
            }
        }

        clearMap()
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
                isPathFinished = false
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
            uiState.copy(newRating = pathRatingRepository.getCurrentRating())
        }
    }

    fun onStartPathButtonClicked() {
        regularLocationUpdateStateFlow.tryEmit(true)
        clearMap()
        mapUiStateFlow.update { uiState ->
            uiState.copy(
                isWritePath = true,
                isPathFinished = false,
                newRating = pathRatingRepository.getCurrentRating()
            )
        }
    }

    fun onStopPathButtonClicked() {
        regularLocationUpdateStateFlow.tryEmit(false)
        mapUiStateFlow.update { uiState ->
            uiState.copy(
                isWritePath = false,
                isPathFinished = true
            )
        }
        lastPaintedPoint = null
    }

    fun onShowAllPathsButtonClicked() {
        if (downloadRatingPathsJob?.isActive == true || mapUiStateFlow.value.showPathsButtonState == ShowPathsButtonState.LOADING) {
            downloadRatingPathsJob?.cancel()
            downloadRatingPathsJob = null
            mapUiStateFlow.update { uiState ->
                uiState.copy(showPathsButtonState = ShowPathsButtonState.DEFAULT)
            }
            newPathInfoListItemStateFlow.tryEmit(
                Pair(
                    -1,
                    PathInfoItemShowButtonState.DEFAULT
                )
            )
        } else {
            if (mapUiStateFlow.value.showPathsButtonState == ShowPathsButtonState.HIDE) {
                clearMap()
                mapUiStateFlow.update { uiState ->
                    uiState.copy(showPathsButtonState = ShowPathsButtonState.DEFAULT)
                }
                newPathInfoListItemStateFlow.tryEmit(
                    Pair(
                        -1,
                        PathInfoItemShowButtonState.DEFAULT
                    )
                )
            } else {
                clearMap()
                mapUiStateFlow.update { uiState ->
                    uiState.copy(showPathsButtonState = ShowPathsButtonState.LOADING)
                }
                newPathInfoListItemStateFlow.tryEmit(
                    Pair(
                        -1,
                        PathInfoItemShowButtonState.LOADING
                    )
                )
                backgroundCachingRatingPathsJob?.cancel()
                downloadRatingPathsJob = viewModelScope.launch {
                    for (path in mapPathsInteractor.getAllSavedRatingPaths()) {
                        showRatingPathOnMap(path)
                        newPathInfoListItemStateFlow.tryEmit(
                            Pair(
                                path.pathId,
                                PathInfoItemShowButtonState.HIDE
                            )
                        )
                    }
                    mapUiStateFlow.update { uiState ->
                        uiState.copy(showPathsButtonState = ShowPathsButtonState.HIDE)
                    }
                }
            }
        }
    }

    fun onRatingButtonClicked(ratingGiven: SegmentRating) {
        pathRatingRepository.setCurrentRating(ratingGiven)
        mapUiStateFlow.update { uiState ->
            uiState.copy(newRating = ratingGiven)
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
                    if (needToUpdateAllPath) {
                        clearMap()
                        lastPaintedPoint = lastSavedPath.pathSegments.first().startPoint
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
            uiState.copy(bottomMenuState = BottomMenuState.PATHS_MENU)
        }

        downloadRatingPathsJob?.cancel()
        backgroundCachingPathsInfoJob?.cancel()

        mapUiStateFlow.update { uiState ->
            uiState.copy(pathsInfoListState = PathsInfoListState.LOADING)
        }
        downloadAllPathsInfoJob = viewModelScope.launch {
            newPathsInfoListFlow.tryEmit(mapPathsInteractor.getAllSavedPathsInfo())
            mapUiStateFlow.update { uiState ->
                uiState.copy(
                    pathsInfoListState = PathsInfoListState.DEFAULT,
                    showPathsButtonState = ShowPathsButtonState.DEFAULT
                )
            }
        }
    }

    fun onHidePathsMenuClicked() {
        downloadRatingPathsJob?.cancel()
        mapUiStateFlow.update { uiState ->
            uiState.copy(
                bottomMenuState = BottomMenuState.DEFAULT,
                showPathsButtonState = ShowPathsButtonState.GONE
            )
        }
    }

    fun onPathInListButtonClicked(
        pathId: Long,
        clickedButtonType: PathsInfoAdapter.PathItemButtonType
    ) {
        when (clickedButtonType) {
            SHOW -> {
                if (showedPathIdsList.contains(pathId)) {
                    hidePathFromMap(pathId)
                    newPathInfoListItemStateFlow.tryEmit(
                        Pair(
                            pathId,
                            PathInfoItemShowButtonState.DEFAULT
                        )
                    )
                } else {
                    newPathInfoListItemStateFlow.tryEmit(
                        Pair(
                            pathId,
                            PathInfoItemShowButtonState.LOADING
                        )
                    )
                    viewModelScope.launch {
                        val savedRatingPath = mapPathsInteractor.getSavedRatingPath(pathId)
                        if (savedRatingPath != null) {
                            showRatingPathOnMap(savedRatingPath)
                            newPathInfoListItemStateFlow.tryEmit(
                                Pair(
                                    pathId,
                                    PathInfoItemShowButtonState.HIDE
                                )
                            )
                        } else {
                            //TODO handle bd error
                        }
                    }
                }
            }
            else -> {
                //TODO
            }
        }
    }

    private fun showRatingPathOnMap(ratingPath: MapRatingPath) {
        if (!showedPathIdsList.contains(ratingPath.pathId)) {
            newRatingPathFlow.tryEmit(ratingPath)
            showedPathIdsList.add(ratingPath.pathId)
        }
    }

    private fun showCommonPathOnMap(commonPath: MapCommonPath) {
        if (!showedPathIdsList.contains(commonPath.pathId)) {
            newCommonPathFlow.tryEmit(commonPath)
            showedPathIdsList.add(commonPath.pathId)
        }
    }

    private fun hidePathFromMap(pathId: Long) {
        if (showedPathIdsList.contains(pathId)) {
            showedPathIdsList.remove(pathId)
            hidePathFlow.tryEmit(pathId)
        }
    }

    private fun clearMap() {
        showedPathIdsList.clear()
        clearMapNowListener?.invoke()
    }

    private fun startBackgroundCachingPaths() {
        backgroundCachingRatingPathsJob = viewModelScope.launch {
            mapPathsInteractor.getAllSavedRatingPaths()
        }
        backgroundCachingCommonPathsJob = viewModelScope.launch {
            mapPathsInteractor.getAllSavedCommonPaths()
        }
        backgroundCachingPathsInfoJob = viewModelScope.launch {
            mapPathsInteractor.getAllSavedPathsInfo()
        }
    }
}
