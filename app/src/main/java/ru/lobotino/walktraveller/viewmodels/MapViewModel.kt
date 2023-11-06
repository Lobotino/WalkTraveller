package ru.lobotino.walktraveller.viewmodels

import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
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
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathRatingRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathsLoaderRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathsSaverRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserRotationRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.ui.PathsInfoAdapter
import ru.lobotino.walktraveller.ui.PathsInfoAdapter.PathItemButtonType.DELETE
import ru.lobotino.walktraveller.ui.PathsInfoAdapter.PathItemButtonType.SHOW
import ru.lobotino.walktraveller.ui.PathsInfoAdapter.PathItemButtonType.SHARE
import ru.lobotino.walktraveller.ui.model.BottomMenuState
import ru.lobotino.walktraveller.ui.model.ConfirmDialogInfo
import ru.lobotino.walktraveller.ui.model.ConfirmDialogType
import ru.lobotino.walktraveller.ui.model.FindMyLocationButtonState
import ru.lobotino.walktraveller.ui.model.MapUiState
import ru.lobotino.walktraveller.ui.model.PathInfoItemShareButtonState
import ru.lobotino.walktraveller.ui.model.PathInfoItemShowButtonState
import ru.lobotino.walktraveller.ui.model.PathInfoItemState
import ru.lobotino.walktraveller.ui.model.MyPathsInfoListState
import ru.lobotino.walktraveller.ui.model.ShowPathsButtonState
import ru.lobotino.walktraveller.ui.model.ShowPathsFilterButtonState
import ru.lobotino.walktraveller.usecases.permissions.GeoPermissionsUseCase
import ru.lobotino.walktraveller.usecases.IUserLocationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapStateInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase
import ru.lobotino.walktraveller.utils.ext.toMapPoint

class MapViewModel(
    private val notificationsPermissionsInteractor: IPermissionsUseCase,
    private val volumeKeysListenerPermissionsInteractor: IPermissionsUseCase,
    private val geoPermissionsUseCase: GeoPermissionsUseCase,
    private val externalStoragePermissionsUseCase: IPermissionsUseCase,
    private val userLocationInteractor: IUserLocationInteractor,
    private val mapPathsInteractor: IMapPathsInteractor,
    private val mapStateInteractor: IMapStateInteractor,
    private val writingPathStatesRepository: IWritingPathStatesRepository,
    private val pathRatingRepository: IPathRatingRepository,
    private val userRotationRepository: IUserRotationRepository,
    private val pathRedactor: IPathRedactor,
    private val pathsSaverRepository: IPathsSaverRepository,
    private val pathsLoaderRepository: IPathsLoaderRepository
) : ViewModel() {

    companion object {
        private val TAG = MapViewModel::class.java.canonicalName
    }

    private var isInitialized = false
    private var updatingYetUnpaintedPaths = false

    private var updateCurrentSavedPath: Job? = null
    private var downloadAllPathsJob: Job? = null
    private var downloadAllPathsInfoJob: Job? = null
    private var backgroundCachingRatingPathsJob: Job? = null
    private var backgroundCachingCommonPathsJob: Job? = null
    private var backgroundCachingPathsInfoJob: Job? = null
    private var lastPaintedPoint: MapPoint? = null

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
    private val newPathInfoListItemFlow =
        MutableSharedFlow<PathInfoItemState>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newCurrentUserLocationFlow =
        MutableSharedFlow<MapPoint>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newConfirmDialogFlow =
        MutableSharedFlow<ConfirmDialogInfo>(1, 0, BufferOverflow.DROP_OLDEST)

    private val writingPathNowState = MutableStateFlow(false)
    private val regularLocationUpdateStateFlow = MutableStateFlow(false)

    private var clearMapNowListener: (() -> Unit)? = null

    private val shareFileChannel = Channel<Uri>()
    private val deletePathInfoItemChannel = Channel<Long>()

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
    val observeNewPathInfoListItemState: Flow<PathInfoItemState> = newPathInfoListItemFlow
    val observeHidePath: Flow<Long> = hidePathFlow
    val observeNewCurrentUserLocation: Flow<MapPoint> = newCurrentUserLocationFlow
    val observeWritingPathNow: Flow<Boolean> = writingPathNowState
    val observeNewConfirmDialog: Flow<ConfirmDialogInfo> = newConfirmDialogFlow
    val observeShareFileChannel = shareFileChannel.consumeAsFlow()
    val observeDeletePathInfoItemChannel = deletePathInfoItemChannel.consumeAsFlow()

    fun observeNewUserRotation(): Flow<Float> = userRotationRepository.observeUserRotation()

    fun observeNeedToClearMapNow(listener: (() -> Unit)?) {
        clearMapNowListener = listener
    }

    fun onInitFinish() {
        setupMapCenterToLastSeenLocation()

        startBackgroundCachingPaths()

        geoPermissionsUseCase.let { geoPermissionsInteractor ->
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

        notificationsPermissionsInteractor.requestPermissions(someDenied = { deniedPermissions ->
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

    fun onResume(extraData: Uri?) {
        if (geoPermissionsUseCase.isGeneralGeoPermissionsGranted()) {
            regularLocationUpdateStateFlow.tryEmit(true)
        }

        if (isInitialized) {
            userRotationRepository.startTrackUserRotation()
            updateNewPointsIfNeeded()
        }

        if (extraData != null) {
            loadAndShowSharedPaths(extraData)
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
        if (downloadAllPathsJob?.isActive == true || mapUiStateFlow.value.myPathsUiState.showPathsButtonState == ShowPathsButtonState.LOADING) {
            downloadAllPathsJob?.cancel()
            downloadAllPathsJob = null
            mapUiStateFlow.update { uiState ->
                uiState.copy(myPathsUiState = uiState.myPathsUiState.copy(showPathsButtonState = ShowPathsButtonState.DEFAULT))
            }
            newPathInfoListItemFlow.tryEmit(
                PathInfoItemState(
                    -1,
                    PathInfoItemShowButtonState.DEFAULT
                )
            )
        } else {
            if (mapUiStateFlow.value.myPathsUiState.showPathsButtonState == ShowPathsButtonState.HIDE) {
                clearMap()
                mapUiStateFlow.update { uiState ->
                    uiState.copy(myPathsUiState = uiState.myPathsUiState.copy(showPathsButtonState = ShowPathsButtonState.DEFAULT))
                }
                newPathInfoListItemFlow.tryEmit(
                    PathInfoItemState(
                        -1,
                        PathInfoItemShowButtonState.DEFAULT
                    )
                )
            } else {
                clearMap()
                mapUiStateFlow.update { uiState ->
                    uiState.copy(myPathsUiState = uiState.myPathsUiState.copy(showPathsButtonState = ShowPathsButtonState.LOADING))
                }
                newPathInfoListItemFlow.tryEmit(
                    PathInfoItemState(
                        -1,
                        PathInfoItemShowButtonState.LOADING
                    )
                )
                backgroundCachingRatingPathsJob?.cancel()

                when (mapUiStateFlow.value.myPathsUiState.showPathsFilterButtonState) {
                    ShowPathsFilterButtonState.RATED_ONLY -> startDownloadAllRatedPaths()
                    ShowPathsFilterButtonState.ALL_IN_COMMON_COLOR -> startDownloadAllPathsAsCommon()
                    else -> {
                        mapUiStateFlow.update { uiState ->
                            uiState.copy(myPathsUiState = uiState.myPathsUiState.copy(showPathsButtonState = ShowPathsButtonState.HIDE))
                        }
                    }
                }
            }
        }
    }

    private fun startDownloadAllRatedPaths() {
        downloadAllPathsJob?.cancel()
        downloadAllPathsJob = viewModelScope.launch {
            for (path in mapPathsInteractor.getAllSavedRatingPaths(true)) {
                showRatingPathOnMap(path)
                newPathInfoListItemFlow.tryEmit(
                    PathInfoItemState(
                        path.pathId,
                        PathInfoItemShowButtonState.HIDE
                    )
                )
            }
            mapUiStateFlow.update { uiState ->
                uiState.copy(myPathsUiState = uiState.myPathsUiState.copy(showPathsButtonState = ShowPathsButtonState.HIDE))
            }
        }
    }

    private fun startDownloadAllPathsAsCommon() {
        downloadAllPathsJob?.cancel()
        downloadAllPathsJob = viewModelScope.launch {
            for (path in mapPathsInteractor.getAllSavedPathsAsCommon()) {
                showCommonPathOnMap(path)
                newPathInfoListItemFlow.tryEmit(
                    PathInfoItemState(
                        path.pathId,
                        PathInfoItemShowButtonState.HIDE
                    )
                )
            }
            mapUiStateFlow.update { uiState ->
                uiState.copy(myPathsUiState = uiState.myPathsUiState.copy(showPathsButtonState = ShowPathsButtonState.HIDE))
            }
        }
    }

    fun onShowPathsFilterButtonClicked() {
        val newFilterValue = when (mapUiStateFlow.value.myPathsUiState.showPathsFilterButtonState) {
            ShowPathsFilterButtonState.RATED_ONLY -> ShowPathsFilterButtonState.ALL_IN_COMMON_COLOR
            ShowPathsFilterButtonState.ALL_IN_COMMON_COLOR -> ShowPathsFilterButtonState.RATED_ONLY
            ShowPathsFilterButtonState.GONE -> ShowPathsFilterButtonState.GONE
        }

        mapUiStateFlow.update { uiState ->
            uiState.copy(
                myPathsUiState = uiState.myPathsUiState.copy(
                    showPathsFilterButtonState = newFilterValue
                )
            )
        }
        when (mapUiStateFlow.value.myPathsUiState.showPathsButtonState) {
            ShowPathsButtonState.LOADING -> {
                when (newFilterValue) {
                    ShowPathsFilterButtonState.RATED_ONLY -> startDownloadAllRatedPaths()
                    ShowPathsFilterButtonState.ALL_IN_COMMON_COLOR -> startDownloadAllPathsAsCommon()
                    else -> {
                        mapUiStateFlow.update { uiState ->
                            uiState.copy(myPathsUiState = uiState.myPathsUiState.copy(showPathsButtonState = ShowPathsButtonState.HIDE))
                        }
                    }
                }
            }

            ShowPathsButtonState.HIDE -> {
                mapUiStateFlow.update { uiState -> uiState.copy(myPathsUiState = uiState.myPathsUiState.copy(showPathsButtonState = ShowPathsButtonState.DEFAULT)) }
            }

            else -> {}
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
            uiState.copy(bottomMenuState = BottomMenuState.MY_PATHS_MENU)
        }

        downloadAllPathsJob?.cancel()
        backgroundCachingPathsInfoJob?.cancel()

        mapUiStateFlow.update { uiState ->
            uiState.copy(myPathsUiState = uiState.myPathsUiState.copy(pathsInfoListState = MyPathsInfoListState.LOADING))
        }
        downloadAllPathsInfoJob = viewModelScope.launch {
            val allSavedPathsList = mapPathsInteractor.getAllSavedPathsInfo()
            if (allSavedPathsList.isNotEmpty()) {
                newPathsInfoListFlow.tryEmit(allSavedPathsList)
                mapUiStateFlow.update { uiState ->
                    uiState.copy(
                        myPathsUiState = uiState.myPathsUiState.copy(
                            pathsInfoListState = MyPathsInfoListState.DEFAULT,
                            showPathsButtonState = ShowPathsButtonState.DEFAULT,
                            showPathsFilterButtonState = ShowPathsFilterButtonState.RATED_ONLY
                        )
                    )
                }
            } else {
                mapUiStateFlow.update { uiState ->
                    uiState.copy(
                        myPathsUiState = uiState.myPathsUiState.copy(
                            pathsInfoListState = MyPathsInfoListState.EMPTY_LIST,
                            showPathsButtonState = ShowPathsButtonState.GONE,
                            showPathsFilterButtonState = ShowPathsFilterButtonState.GONE
                        )
                    )
                }
            }
        }
    }

    fun onPathsMenuBackButtonClicked() {
        downloadAllPathsJob?.cancel()
        mapUiStateFlow.update { uiState ->
            uiState.copy(
                bottomMenuState = BottomMenuState.DEFAULT,
                myPathsUiState = uiState.myPathsUiState.copy(
                    showPathsButtonState = ShowPathsButtonState.GONE,
                    showPathsFilterButtonState = ShowPathsFilterButtonState.GONE
                )
            )
        }
    }

    fun onPathInMyListButtonClicked(
        pathId: Long,
        clickedButtonType: PathsInfoAdapter.PathItemButtonType
    ) {
        when (clickedButtonType) {
            SHOW -> {
                if (showedPathIdsList.contains(pathId)) {
                    hidePathFromMap(pathId)
                    newPathInfoListItemFlow.tryEmit(
                        PathInfoItemState(
                            pathId,
                            PathInfoItemShowButtonState.DEFAULT
                        )
                    )
                } else {
                    newPathInfoListItemFlow.tryEmit(
                        PathInfoItemState(
                            pathId,
                            PathInfoItemShowButtonState.LOADING
                        )
                    )
                    viewModelScope.launch {
                        val savedRatingPath = mapPathsInteractor.getSavedRatingPath(pathId, false)
                        if (savedRatingPath != null) {
                            showRatingPathOnMap(savedRatingPath)
                            newPathInfoListItemFlow.tryEmit(
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

            SHARE -> {
                checkPermissionsAndSharePath(pathId)
            }
        }
    }

    private fun checkPermissionsAndSharePath(pathId: Long) {
        if (externalStoragePermissionsUseCase.isPermissionsGranted()) {
            sharePath(pathId)
        } else {
            externalStoragePermissionsUseCase.requestPermissions(
                allGranted = {
                    sharePath(pathId)
                },
                someDenied = { deniedPermissions ->
                    permissionsDeniedSharedFlow.tryEmit(
                        deniedPermissions
                    )
                }
            )
        }
    }

    private fun sharePath(pathId: Long) {
        newPathInfoListItemFlow.tryEmit(
            PathInfoItemState(
                pathId,
                shareButtonState = PathInfoItemShareButtonState.LOADING
            )
        )

        viewModelScope.launch {
            val path = mapPathsInteractor.getSavedRatingPath(pathId, false)
            if (path != null) {
                try {
                    shareFileChannel.trySend(pathsSaverRepository.saveRatingPath(path))
                } catch (exception: IOException) {
                    //TODO show toast error
                    Log.w(TAG, exception)
                } finally {
                    newPathInfoListItemFlow.tryEmit(
                        PathInfoItemState(
                            pathId,
                            shareButtonState = PathInfoItemShareButtonState.DEFAULT
                        )
                    )
                }
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
                    .requestPermissions({
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
        deletePathInfoItemChannel.trySend(pathId)
    }

    private fun checkIsPathsListNotEmptyNow() {
        viewModelScope.launch {
            if (mapPathsInteractor.getAllSavedPathsInfo().isEmpty()) {
                mapUiStateFlow.update { uiState ->
                    uiState.copy(
                        myPathsUiState = uiState.myPathsUiState.copy(
                            pathsInfoListState = MyPathsInfoListState.EMPTY_LIST,
                            showPathsButtonState = ShowPathsButtonState.GONE,
                            showPathsFilterButtonState = ShowPathsFilterButtonState.GONE
                        )
                    )
                }
            }
        }
    }

    fun onLocationPermissionDialogConfirmed() {
        geoPermissionsUseCase.let { geoPermissionsInteractor ->
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

    private fun loadAndShowSharedPaths(sharedFileUri: Uri) {
        viewModelScope.launch {
            val sharedPathsSegments = pathsLoaderRepository.loadAllRatingPathsFromFile(sharedFileUri)
            Log.d("Test", sharedPathsSegments.toString())

            //TODO show other user path
        }
    }
}
