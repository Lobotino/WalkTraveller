package ru.lobotino.walktraveller.viewmodels

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathRatingRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserRotationRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.ui.PathsInfoAdapter
import ru.lobotino.walktraveller.ui.PathsInfoAdapter.PathItemButtonType.DELETE
import ru.lobotino.walktraveller.ui.PathsInfoAdapter.PathItemButtonType.SHOW
import ru.lobotino.walktraveller.ui.model.BottomMenuState
import ru.lobotino.walktraveller.ui.model.ConfirmDialogInfo
import ru.lobotino.walktraveller.ui.model.ConfirmDialogType
import ru.lobotino.walktraveller.ui.model.FindMyLocationButtonState
import ru.lobotino.walktraveller.ui.model.MapUiState
import ru.lobotino.walktraveller.ui.model.PathInfoItemShowButtonState
import ru.lobotino.walktraveller.ui.model.PathInfoItemState
import ru.lobotino.walktraveller.ui.model.PathsInfoListState
import ru.lobotino.walktraveller.ui.model.ShowPathsButtonState
import ru.lobotino.walktraveller.usecases.GeoPermissionsInteractor
import ru.lobotino.walktraveller.usecases.IUserLocationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapStateInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsInteractor
import ru.lobotino.walktraveller.utils.ext.toMapPoint

class MapViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private val TAG = MapViewModel::class.java.canonicalName
    }

    private var isInitialized = false
    private var updatingYetUnpaintedPaths = false

    private var updateCurrentSavedPath: Job? = null
    private var downloadRatingPathsJob: Job? = null
    private var downloadAllPathsInfoJob: Job? = null
    private var backgroundCachingRatingPathsJob: Job? = null
    private var backgroundCachingCommonPathsJob: Job? = null
    private var backgroundCachingPathsInfoJob: Job? = null
    private var lastPaintedPoint: MapPoint? = null

    private var notificationsPermissionsInteractor: IPermissionsInteractor? = null
    private var volumeKeysListenerPermissionsInteractor: IPermissionsInteractor? = null
    private var geoPermissionsInteractor: GeoPermissionsInteractor? = null


    private lateinit var userLocationInteractor: IUserLocationInteractor
    private lateinit var mapPathsInteractor: IMapPathsInteractor
    private lateinit var mapStateInteractor: IMapStateInteractor

    private lateinit var writingPathStatesRepository: IWritingPathStatesRepository
    private lateinit var pathRatingRepository: IPathRatingRepository
    private lateinit var userRotationRepository: IUserRotationRepository
    private lateinit var pathRedactor: IPathRedactor

    private var showedPathIdsList: MutableList<Long> = ArrayList()

    private val permissionsDeniedSharedFlow =
        MutableSharedFlow<List<String>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val hidePathFlow =
        MutableSharedFlow<Long>(1, 0, BufferOverflow.DROP_OLDEST)
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
        MutableSharedFlow<PathInfoItemState>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newCurrentUserLocationFlow =
        MutableSharedFlow<MapPoint>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newConfirmDialogFlow =
        MutableSharedFlow<ConfirmDialogInfo>(1, 0, BufferOverflow.DROP_OLDEST)

    private val writingPathNowState = MutableStateFlow(false)
    private val regularLocationUpdateStateFlow = MutableStateFlow(false)

    private var clearMapNowListener: (() -> Unit)? = null

    private val mapUiStateFlow =
        MutableStateFlow(
            MapUiState(
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
    val observeNewPathInfoListItemState: Flow<PathInfoItemState> = newPathInfoListItemStateFlow
    val observeHidePath: Flow<Long> = hidePathFlow
    val observeNewCurrentUserLocation: Flow<MapPoint> = newCurrentUserLocationFlow
    val observeWritingPathNow: Flow<Boolean> = writingPathNowState
    val observeNewConfirmDialog: Flow<ConfirmDialogInfo> = newConfirmDialogFlow

    fun observeNewUserRotation(): Flow<Float> = userRotationRepository.observeUserRotation()

    fun observeNeedToClearMapNow(listener: (() -> Unit)?) {
        clearMapNowListener = listener
    }

    fun setGeoPermissionsInteractor(geoPermissionsInteractor: GeoPermissionsInteractor) {
        this.geoPermissionsInteractor = geoPermissionsInteractor
    }

    fun setNotificationsPermissionsInteractor(notificationsPermissionsInteractor: IPermissionsInteractor) {
        this.notificationsPermissionsInteractor = notificationsPermissionsInteractor
    }

    fun setVolumeKeysListenerPermissionsInteractor(volumeKeysListenerPermissionsInteractor: IPermissionsInteractor) {
        this.volumeKeysListenerPermissionsInteractor = volumeKeysListenerPermissionsInteractor
    }

    fun setMapPathInteractor(mapPathsInteractor: IMapPathsInteractor) {
        this.mapPathsInteractor = mapPathsInteractor
    }

    fun setMapStateInteractor(mapStateInteractor: IMapStateInteractor) {
        this.mapStateInteractor = mapStateInteractor
    }

    fun setPathRedactor(pathRedactor: IPathRedactor) {
        this.pathRedactor = pathRedactor
    }

    fun setWritingPathStatesRepository(writingPathStatesRepository: IWritingPathStatesRepository) {
        this.writingPathStatesRepository = writingPathStatesRepository
    }

    fun setPathRatingRepository(pathRatingRepository: IPathRatingRepository) {
        this.pathRatingRepository = pathRatingRepository
    }

    fun setUserLocationInteractor(userLocationInteractor: IUserLocationInteractor) {
        this.userLocationInteractor = userLocationInteractor
    }

    fun setUserRotationRepository(userRotationRepository: IUserRotationRepository) {
        this.userRotationRepository = userRotationRepository
    }

    fun onInitFinish() {
        setupMapCenterToLastSeenLocation()

        startBackgroundCachingPaths()

        geoPermissionsInteractor?.let { geoPermissionsInteractor ->
            if (geoPermissionsInteractor.isGeneralGeoPermissionsGranted()) {
                regularLocationUpdateStateFlow.tryEmit(true)
                updateCurrentMapCenterToUserLocation()
            } else {
                newConfirmDialogFlow.tryEmit(
                    ConfirmDialogInfo(
                        ConfirmDialogType.GEO_LOCATION_PERMISSION_REQUIRED,
                        null
                    )
                )
            }
        }

        notificationsPermissionsInteractor?.requestPermissions(someDenied = { deniedPermissions ->
            permissionsDeniedSharedFlow.tryEmit(
                deniedPermissions
            )
        })

        userRotationRepository.startTrackUserRotation()

        clearMap()

        syncWritingPathState()

        isInitialized = true
    }

    private fun setupMapCenterToLastSeenLocation() {
        newMapCenterFlow.tryEmit(mapStateInteractor.getLastSeenPoint())
    }

    private fun syncWritingPathState() {
        writingPathStatesRepository.isWritingPathNow().let { isWritingPathNow ->
            if (isWritingPathNow != writingPathNowState.value) {
                if (isWritingPathNow) {
                    updateNewPointsIfNeeded()
                } else {
                    writingPathNowState.tryEmit(false)
                }
            }
        }
        pathRatingRepository.getCurrentRating().let { currentRating ->
            if (currentRating != mapUiStateFlow.value.newRating) {
                mapUiStateFlow.update { mapUiState -> mapUiState.copy(newRating = currentRating) }
            }
        }
    }

    fun onResume() {
        if (geoPermissionsInteractor?.isGeneralGeoPermissionsGranted() == true) {
            regularLocationUpdateStateFlow.tryEmit(true)
        }

        if (isInitialized) {
            userRotationRepository.startTrackUserRotation()
            updateNewPointsIfNeeded()
        }
    }

    fun onPause() {
        userRotationRepository.stopTrackUserRotation()
        updatingYetUnpaintedPaths = false
        if (!writingPathStatesRepository.isWritingPathNow()) {
            regularLocationUpdateStateFlow.tryEmit(false)
        }
    }

    fun onNewLocationReceive(location: Location) {
        newCurrentUserLocationFlow.tryEmit(location.toMapPoint())

        if (writingPathStatesRepository.isWritingPathNow() && !updatingYetUnpaintedPaths) {
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
        clearMap()
        writingPathStatesRepository.setWritingPathNow(true)
        writingPathNowState.tryEmit(true)
        mapUiStateFlow.update { uiState ->
            uiState.copy(
                isPathFinished = false,
                newRating = pathRatingRepository.getCurrentRating()
            )
        }
    }

    fun onStopPathButtonClicked() {
        writingPathStatesRepository.setWritingPathNow(false)
        writingPathNowState.tryEmit(false)
        mapUiStateFlow.update { uiState ->
            uiState.copy(
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
                PathInfoItemState(
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
                    PathInfoItemState(
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
                    PathInfoItemState(
                        -1,
                        PathInfoItemShowButtonState.LOADING
                    )
                )
                backgroundCachingRatingPathsJob?.cancel()
                downloadRatingPathsJob = viewModelScope.launch {
                    for (path in mapPathsInteractor.getAllSavedRatingPaths(true)) {
                        showRatingPathOnMap(path)
                        newPathInfoListItemStateFlow.tryEmit(
                            PathInfoItemState(
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

    private fun updateNewPointsIfNeeded() {
        if (!updatingYetUnpaintedPaths) {
            updatingYetUnpaintedPaths = true
            if (writingPathStatesRepository.isWritingPathNow()) {

                if (!writingPathNowState.value) {
                    writingPathNowState.tryEmit(true)
                }

                updateCurrentSavedPath?.cancel()
                updateCurrentSavedPath = viewModelScope.launch {
                    mapPathsInteractor.getLastSavedRatingPath()?.let { lastSavedPath ->

                        if (lastSavedPath.pathSegments.isNotEmpty()) {
                            clearMap()
                            redrawAllPathSegments(lastSavedPath.pathSegments)
                            newCurrentUserLocationFlow.tryEmit(lastSavedPath.pathSegments.last().finishPoint)
                        }
                        updatingYetUnpaintedPaths = false
                    }
                }
            } else {
                updatingYetUnpaintedPaths = false
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

    private fun redrawAllPathSegments(allPathSegment: List<MapPathSegment>) {
        lastPaintedPoint = allPathSegment.last().finishPoint
        for (segment in allPathSegment) {
            newPathSegmentFlow.tryEmit(segment)
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
            val allSavedPathsList = mapPathsInteractor.getAllSavedPathsInfo()
            if (allSavedPathsList.isNotEmpty()) {
                newPathsInfoListFlow.tryEmit(allSavedPathsList)
                mapUiStateFlow.update { uiState ->
                    uiState.copy(
                        pathsInfoListState = PathsInfoListState.DEFAULT,
                        showPathsButtonState = ShowPathsButtonState.DEFAULT
                    )
                }
            } else {
                mapUiStateFlow.update { uiState ->
                    uiState.copy(
                        pathsInfoListState = PathsInfoListState.EMPTY_LIST,
                        showPathsButtonState = ShowPathsButtonState.GONE
                    )
                }
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
                        PathInfoItemState(
                            pathId,
                            PathInfoItemShowButtonState.DEFAULT
                        )
                    )
                } else {
                    newPathInfoListItemStateFlow.tryEmit(
                        PathInfoItemState(
                            pathId,
                            PathInfoItemShowButtonState.LOADING
                        )
                    )
                    viewModelScope.launch {
                        val savedRatingPath = mapPathsInteractor.getSavedRatingPath(pathId, false)
                        if (savedRatingPath != null) {
                            showRatingPathOnMap(savedRatingPath)
                            newPathInfoListItemStateFlow.tryEmit(
                                PathInfoItemState(
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

            DELETE -> {
                newConfirmDialogFlow.tryEmit(
                    ConfirmDialogInfo(
                        ConfirmDialogType.DELETE_PATH,
                        pathId
                    )
                )
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
            mapPathsInteractor.getAllSavedRatingPaths(false)
        }
        backgroundCachingCommonPathsJob = viewModelScope.launch {
            mapPathsInteractor.getAllSavedCommonPaths()
        }
        backgroundCachingPathsInfoJob = viewModelScope.launch {
            mapPathsInteractor.getAllSavedPathsInfo()
        }
    }

    fun onFindMyLocationButtonClicked() {
        geoPermissionsInteractor?.let { geoPermissionsInteractor ->
            if (geoPermissionsInteractor.isGeneralGeoPermissionsGranted()) {
                updateCurrentMapCenterToUserLocation()
            } else {
                geoPermissionsInteractor.requestPermissions({
                    regularLocationUpdateStateFlow.tryEmit(true)
                    updateCurrentMapCenterToUserLocation()
                }, {
                    if (geoPermissionsInteractor.isGeneralGeoPermissionsGranted()) {
                        regularLocationUpdateStateFlow.tryEmit(true)
                        updateCurrentMapCenterToUserLocation()
                    } else {
                        mapUiStateFlow.update { mapUiState ->
                            mapUiState.copy(
                                findMyLocationButtonState = FindMyLocationButtonState.ERROR
                            )
                        }
                    }
                })
            }
        }
    }

    fun onMapScrolled(mapPoint: MapPoint) {
        mapStateInteractor.setLastSeenPoint(mapPoint)

        if (mapUiStateFlow.value.findMyLocationButtonState == FindMyLocationButtonState.CENTER_ON_CURRENT_LOCATION) {
            mapUiStateFlow.update { mapUiState -> mapUiState.copy(findMyLocationButtonState = FindMyLocationButtonState.DEFAULT) }
        }
    }

    fun onMapZoomed() {
        //TODO
    }

    private fun updateCurrentMapCenterToUserLocation() {
        val setLoadingStateWithDelayJob = viewModelScope.launch {
            delay(50L)
            mapUiStateFlow.update { mapUiState -> mapUiState.copy(findMyLocationButtonState = FindMyLocationButtonState.LOADING) }
        }

        val setLocationButtonStateToError = {
            mapUiStateFlow.update { mapUiState -> mapUiState.copy(findMyLocationButtonState = FindMyLocationButtonState.ERROR) }
        }

        userLocationInteractor.getCurrentUserLocation(
            { location ->
                setLoadingStateWithDelayJob.cancel()
                newMapCenterFlow.tryEmit(location)
                mapUiStateFlow.update { mapUiState -> mapUiState.copy(findMyLocationButtonState = FindMyLocationButtonState.CENTER_ON_CURRENT_LOCATION) }
            }, {
                setLoadingStateWithDelayJob.cancel()
                setLocationButtonStateToError()
            }, { error ->
                Log.e(TAG, error.message, error)
                setLoadingStateWithDelayJob.cancel()
                setLocationButtonStateToError()
            }
        )
    }

    fun onConfirmPathDelete(pathId: Long) {
        viewModelScope.launch {
            pathRedactor.deletePath(pathId)
            checkIsPathsListNotEmptyNow()
        }
        hidePathFromMap(pathId)
        newPathInfoListItemStateFlow.tryEmit(
            PathInfoItemState(
                pathId,
                PathInfoItemShowButtonState.DEFAULT,
                true
            )
        )
    }

    private fun checkIsPathsListNotEmptyNow() {
        viewModelScope.launch {
            if (mapPathsInteractor.getAllSavedPathsInfo().isEmpty()) {
                mapUiStateFlow.update { uiState ->
                    uiState.copy(
                        pathsInfoListState = PathsInfoListState.EMPTY_LIST,
                        showPathsButtonState = ShowPathsButtonState.GONE
                    )
                }
            }
        }
    }

    fun onLocationPermissionDialogConfirmed() {
        geoPermissionsInteractor?.let { geoPermissionsInteractor ->
            geoPermissionsInteractor.requestPermissions(allGranted = {
                regularLocationUpdateStateFlow.tryEmit(true)
                updateCurrentMapCenterToUserLocation()
            }, someDenied = { deniedPermissions ->
                if (geoPermissionsInteractor.isGeneralGeoPermissionsGranted()) {
                    regularLocationUpdateStateFlow.tryEmit(true)
                    updateCurrentMapCenterToUserLocation()
                } else {
                    mapUiStateFlow.update { mapUiState -> mapUiState.copy(findMyLocationButtonState = FindMyLocationButtonState.ERROR) }
                    permissionsDeniedSharedFlow.tryEmit(deniedPermissions)
                }
            })
        }
    }
}
