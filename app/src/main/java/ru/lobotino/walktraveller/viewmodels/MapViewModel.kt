package ru.lobotino.walktraveller.viewmodels

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathRatingRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserRotationRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.ui.model.BottomMenuState
import ru.lobotino.walktraveller.ui.model.ConfirmDialogType
import ru.lobotino.walktraveller.ui.model.FindMyLocationButtonState
import ru.lobotino.walktraveller.ui.model.MapUiState
import ru.lobotino.walktraveller.ui.model.PathsToAction
import ru.lobotino.walktraveller.usecases.IUserLocationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapStateInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase
import ru.lobotino.walktraveller.usecases.permissions.GeoPermissionsUseCase
import ru.lobotino.walktraveller.utils.ext.toMapPoint

class MapViewModel(
    private val notificationsPermissionsUseCase: IPermissionsUseCase,
    private val volumeKeysListenerPermissionsUseCase: IPermissionsUseCase,
    private val geoPermissionsUseCase: GeoPermissionsUseCase,
    private val userLocationInteractor: IUserLocationInteractor,
    private val mapPathsInteractor: IMapPathsInteractor,
    private val mapStateInteractor: IMapStateInteractor,
    private val writingPathStatesRepository: IWritingPathStatesRepository,
    private val pathRatingRepository: IPathRatingRepository,
    private val userRotationRepository: IUserRotationRepository,
    private val userInfoRepository: IUserInfoRepository
) : ViewModel() {

    companion object {
        private val TAG = MapViewModel::class.java.canonicalName
    }

    private val mapUiStateFlow =
        MutableStateFlow(
            MapUiState(
                isPathFinished = false
            )
        )

    private val writingPathNowState = MutableStateFlow(false)
    private val regularLocationUpdateStateFlow = MutableStateFlow(false)

    private val permissionsDeniedSharedFlow =
        MutableSharedFlow<List<String>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val hidePathFlow =
        MutableSharedFlow<PathsToAction>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newCurrentPathSegmentsFlow =
        MutableSharedFlow<List<MapPathSegment>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newCommonPathFlow =
        MutableSharedFlow<List<MapCommonPath>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newRatingPathFlow =
        MutableSharedFlow<List<MapRatingPath>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newMapCenterFlow =
        MutableSharedFlow<MapPoint>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newCurrentUserLocationFlow =
        MutableSharedFlow<MapPoint>(1, 0, BufferOverflow.DROP_OLDEST)

    private val newConfirmDialogChannel = Channel<ConfirmDialogType>()

    val observePermissionsDeniedResult: Flow<List<String>> = permissionsDeniedSharedFlow
    val observeNewCurrentPathSegments: Flow<List<MapPathSegment>> = newCurrentPathSegmentsFlow
    val observeNewCommonPath: Flow<List<MapCommonPath>> = newCommonPathFlow
    val observeNewRatingPath: Flow<List<MapRatingPath>> = newRatingPathFlow
    val observeMapUiState: Flow<MapUiState> = mapUiStateFlow
    val observeRegularLocationUpdate: Flow<Boolean> = regularLocationUpdateStateFlow
    val observeNewMapCenter: Flow<MapPoint> = newMapCenterFlow
    val observeHidePath: Flow<PathsToAction> = hidePathFlow
    val observeNewCurrentUserLocation: Flow<MapPoint> = newCurrentUserLocationFlow
    val observeWritingPathNow: Flow<Boolean> = writingPathNowState
    val observeNewConfirmDialog: Flow<ConfirmDialogType> = newConfirmDialogChannel.consumeAsFlow()

    private var isInitialized = false
    private var updatingYetUnpaintedPaths = false
    private var updateCurrentSavedPathJob: Job? = null
    private var backgroundCachingRatingPathsJob: Job? = null
    private var backgroundCachingCommonPathsJob: Job? = null
    private var backgroundCachingPathsInfoJob: Job? = null
    private var lastPaintedPoint: MapPoint? = null
    private var showedPathIdsSet: MutableSet<Long> = HashSet()

    fun observeNewUserRotation(): Flow<Float> = userRotationRepository.observeUserRotation()

    fun onInitFinish() {
        setupMapCenterToLastSeenLocation()

        startBackgroundCachingPaths()

        checkPermissions()

        userRotationRepository.startTrackUserRotation()

        clearMap()

        syncWritingPathState()

        isInitialized = true
    }

    private fun checkPermissions() {
        if (geoPermissionsUseCase.isGeneralGeoPermissionsGranted()) {
            regularLocationUpdateStateFlow.tryEmit(true)
            updateCurrentMapCenterToUserLocation()
            checkNotificationPermissions()
        } else {
            newConfirmDialogChannel.trySend(
                ConfirmDialogType.GeoLocationPermissionRequired
            )
        }
    }

    private fun checkNotificationPermissions() {
        notificationsPermissionsUseCase.requestPermissions(someDenied = { deniedPermissions ->
            permissionsDeniedSharedFlow.tryEmit(
                deniedPermissions
            )
        })
    }

    private fun needToAskVolumeButtonsPermissions(): Boolean {
        return userInfoRepository.needToSuggestVolumeFeature() && !volumeKeysListenerPermissionsUseCase.isPermissionsGranted()
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
        if (geoPermissionsUseCase.isGeneralGeoPermissionsGranted()) {
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
        if (needToAskVolumeButtonsPermissions()) {
            newConfirmDialogChannel.trySend(ConfirmDialogType.VolumeButtonsFeatureRequest)
        } else {
            startPathTracking()
        }
    }

    private fun startPathTracking() {
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

                updateCurrentSavedPathJob?.cancel()
                updateCurrentSavedPathJob = viewModelScope.launch {
                    val lastSavedPath = mapPathsInteractor.getLastSavedRatingPath()
                    if (lastSavedPath != null && lastSavedPath.pathSegments.isNotEmpty()) {
                        redrawAllPathSegments(lastSavedPath)
                        newCurrentUserLocationFlow.tryEmit(lastSavedPath.pathSegments.last().finishPoint)
                    }
                }.also {
                    it.invokeOnCompletion {
                        updatingYetUnpaintedPaths = false
                    }
                }
            } else {
                updatingYetUnpaintedPaths = false
            }
        }
    }

    private fun drawNewSegmentToPoint(newPoint: MapPoint, segmentRating: SegmentRating) {
        val lastPaintedPoint = lastPaintedPoint
        if (lastPaintedPoint != null) {
            newCurrentPathSegmentsFlow.tryEmit(
                listOf(MapPathSegment(lastPaintedPoint, newPoint, segmentRating))
            )
        }
        this.lastPaintedPoint = newPoint
    }

    private fun redrawAllPathSegments(path: MapRatingPath) {
        clearMap()
        lastPaintedPoint = path.pathSegments.last().finishPoint
        newCurrentPathSegmentsFlow.tryEmit(path.pathSegments)
    }

    fun showRatingPathOnMap(ratingPath: MapRatingPath) {
        if (!showedPathIdsSet.contains(ratingPath.pathId)) {
            newRatingPathFlow.tryEmit(listOf(ratingPath))
            showedPathIdsSet.add(ratingPath.pathId)
        }
    }

    fun showCommonPathOnMap(commonPath: MapCommonPath) {
        if (!showedPathIdsSet.contains(commonPath.pathId)) {
            newCommonPathFlow.tryEmit(listOf(commonPath))
            showedPathIdsSet.add(commonPath.pathId)
        }
    }

    fun showRatingPathListOnMap(ratingPaths: List<MapRatingPath>) {
        val pathsToShow = ratingPaths.filter { !showedPathIdsSet.contains(it.pathId) }
        newRatingPathFlow.tryEmit(pathsToShow)
        showedPathIdsSet.addAll(pathsToShow.map { it.pathId })
    }

    fun showCommonPathListOnMap(commonPaths: List<MapCommonPath>) {
        val pathsToShow = commonPaths.filter { !showedPathIdsSet.contains(it.pathId) }
        newCommonPathFlow.tryEmit(pathsToShow)
        showedPathIdsSet.addAll(pathsToShow.map { it.pathId })
    }

    fun hidePathsFromMap(pathsToHide: PathsToAction) {
        when (pathsToHide) {
            PathsToAction.All -> {
                clearMap()
            }

            is PathsToAction.Single -> {
                val pathId = pathsToHide.pathId
                if (showedPathIdsSet.contains(pathId)) {
                    showedPathIdsSet.remove(pathId)
                    hidePathFlow.tryEmit(PathsToAction.Single(pathId))
                }
            }

            is PathsToAction.Multiple -> {
                showedPathIdsSet.removeAll(pathsToHide.pathIds.toSet())
                hidePathFlow.tryEmit(PathsToAction.Multiple(pathsToHide.pathIds))
            }
        }
    }

    fun clearMap() {
        showedPathIdsSet.clear()
        hidePathFlow.tryEmit(PathsToAction.All)
    }

    private fun startBackgroundCachingPaths() {
        backgroundCachingRatingPathsJob = viewModelScope.launch {
            mapPathsInteractor.getAllSavedRatingPaths(false)
        }
        backgroundCachingCommonPathsJob = viewModelScope.launch {
            mapPathsInteractor.getAllSavedPathsAsCommon()
        }
        backgroundCachingPathsInfoJob = viewModelScope.launch {
            mapPathsInteractor.getAllSavedPathsInfo()
        }
    }

    fun onFindMyLocationButtonClicked() {
        geoPermissionsUseCase.let { geoPermissionsInteractor ->
            if (geoPermissionsInteractor.isGeneralGeoPermissionsGranted()) {
                updateCurrentMapCenterToUserLocation()
            } else {
                geoPermissionsInteractor
                    .requestPermissions(
                        {
                            regularLocationUpdateStateFlow.tryEmit(true)
                            updateCurrentMapCenterToUserLocation()
                        },
                        {
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
                        }
                    )
            }
        }
    }

    fun onMapScrolled(mapPoint: MapPoint) {
        mapStateInteractor.setLastSeenPoint(mapPoint)

        if (mapUiStateFlow.value.findMyLocationButtonState == FindMyLocationButtonState.CENTER_ON_CURRENT_LOCATION) {
            mapUiStateFlow.update { mapUiState ->
                mapUiState.copy(
                    findMyLocationButtonState = FindMyLocationButtonState.DEFAULT
                )
            }
        }
    }

    fun onMapZoomed() {
        // TODO
    }

    private fun updateCurrentMapCenterToUserLocation() {
        val setLoadingStateWithDelayJob = viewModelScope.launch {
            delay(50L)
            mapUiStateFlow.update { mapUiState ->
                mapUiState.copy(
                    findMyLocationButtonState = FindMyLocationButtonState.LOADING
                )
            }
        }

        val setLocationButtonStateToError = {
            mapUiStateFlow.update { mapUiState ->
                mapUiState.copy(
                    findMyLocationButtonState = FindMyLocationButtonState.ERROR
                )
            }
        }

        userLocationInteractor.getCurrentUserLocation(
            { location ->
                setLoadingStateWithDelayJob.cancel()
                newMapCenterFlow.tryEmit(location)
                mapUiStateFlow.update { mapUiState ->
                    mapUiState.copy(
                        findMyLocationButtonState = FindMyLocationButtonState.CENTER_ON_CURRENT_LOCATION
                    )
                }
            },
            {
                setLoadingStateWithDelayJob.cancel()
                setLocationButtonStateToError()
            },
            { error ->
                Log.e(TAG, error.message, error)
                setLoadingStateWithDelayJob.cancel()
                setLocationButtonStateToError()
            }
        )
    }

    fun onLocationPermissionDialogConfirmed() {
        geoPermissionsUseCase.requestPermissions(allGranted = {
            regularLocationUpdateStateFlow.tryEmit(true)
            updateCurrentMapCenterToUserLocation()
        }, someDenied = { deniedPermissions ->
            if (geoPermissionsUseCase.isGeneralGeoPermissionsGranted()) {
                regularLocationUpdateStateFlow.tryEmit(true)
                updateCurrentMapCenterToUserLocation()
            } else {
                mapUiStateFlow.update { mapUiState ->
                    mapUiState.copy(
                        findMyLocationButtonState = FindMyLocationButtonState.ERROR
                    )
                }
                permissionsDeniedSharedFlow.tryEmit(deniedPermissions)
            }
        })
        checkNotificationPermissions()
    }

    fun onBottomMenuStateChange(newBottomMenuState: BottomMenuState) {
        mapUiStateFlow.update { uiState -> uiState.copy(bottomMenuState = newBottomMenuState) }
    }

    fun onVolumeFeatureSuggestAccepted() {
        userInfoRepository.setNeedToSuggestVolumeFeature(false)
        newConfirmDialogChannel.trySend(ConfirmDialogType.VolumeButtonsFeatureInfo)
    }

    fun onVolumeFeatureSuggestDecline() {
        userInfoRepository.setNeedToSuggestVolumeFeature(false)
        startPathTracking()
    }

    fun onVolumeFeaturePermissionsInfoConfirm() {
        volumeKeysListenerPermissionsUseCase.requestPermissions(
            allGranted = {
                startPathTracking()
            }, someDenied = {
                startPathTracking()
                //TODO show toast error
            }
        )
    }
}
