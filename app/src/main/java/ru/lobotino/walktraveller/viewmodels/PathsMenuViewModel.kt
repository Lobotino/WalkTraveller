package ru.lobotino.walktraveller.viewmodels

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lobotino.walktraveller.analytics.AnalyticsEvent
import ru.lobotino.walktraveller.analytics.AnalyticsPathType
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathsSaverRepository
import ru.lobotino.walktraveller.ui.maplibre.pathBounds
import ru.lobotino.walktraveller.ui.model.BottomMenuState
import ru.lobotino.walktraveller.ui.model.ConfirmDialogType
import ru.lobotino.walktraveller.ui.model.DeletePathInfoItemEvent
import ru.lobotino.walktraveller.ui.model.MapEvent
import ru.lobotino.walktraveller.ui.model.MyPathsInfoListState
import ru.lobotino.walktraveller.ui.model.MyPathsUiState
import ru.lobotino.walktraveller.ui.model.NewPathInfoItemState
import ru.lobotino.walktraveller.ui.model.NewPathInfoListEvent
import ru.lobotino.walktraveller.ui.model.OuterPathsInfoListState
import ru.lobotino.walktraveller.ui.model.OuterPathsUiState
import ru.lobotino.walktraveller.ui.model.PathInfoItemShareButtonState
import ru.lobotino.walktraveller.ui.model.PathInfoItemShowButtonState
import ru.lobotino.walktraveller.ui.model.PathInfoItemState
import ru.lobotino.walktraveller.ui.model.PathItemButtonType
import ru.lobotino.walktraveller.ui.model.PathsMenuType
import ru.lobotino.walktraveller.ui.model.PathsToAction
import ru.lobotino.walktraveller.ui.model.ShowPathsButtonState
import ru.lobotino.walktraveller.ui.model.ShowPathsFilterButtonState
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IOuterPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase
import java.io.IOException

class PathsMenuViewModel(
    private val pathsSaverRepository: IPathsSaverRepository,
    private val externalStoragePermissionsUseCase: IPermissionsUseCase,
    private val mapPathsInteractor: IMapPathsInteractor,
    private val outerPathsInteractor: IOuterPathsInteractor,
    private val pathRedactor: IPathRedactor,
    private val analyticsTracker: IAnalyticsTracker
) : ViewModel() {

    private val myPathsMenuUiStateFlow =
        MutableStateFlow(
            MyPathsUiState()
        )

    private val outerPathsMenuUiStateFlow =
        MutableStateFlow(
            OuterPathsUiState()
        )

    private val permissionsDeniedSharedFlow =
        MutableSharedFlow<List<String>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newPathInfoListItemStateFlow =
        MutableSharedFlow<NewPathInfoItemState>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newPathsInfoListFlow =
        MutableSharedFlow<NewPathInfoListEvent>(1, 0, BufferOverflow.DROP_OLDEST)

    private val shareFileChannel = Channel<Uri>()
    private val deletePathInfoItemChannel = Channel<DeletePathInfoItemEvent>()
    private val newConfirmDialogChannel = Channel<ConfirmDialogType>()
    private val newMapEventChannel = Channel<MapEvent>(64, BufferOverflow.DROP_OLDEST)

    val observeShareFileChannel = shareFileChannel.consumeAsFlow()
    val observeDeletePathInfoItemChannel = deletePathInfoItemChannel.consumeAsFlow()
    val observeNewConfirmDialog: Flow<ConfirmDialogType> = newConfirmDialogChannel.consumeAsFlow()
    val observeNewMapEvent: Flow<MapEvent> = newMapEventChannel.consumeAsFlow()
    val observeNewPathInfoListItemState: Flow<NewPathInfoItemState> = newPathInfoListItemStateFlow
    val observeMyPathsMenuUiState: Flow<MyPathsUiState> = myPathsMenuUiStateFlow
    val observeOuterPathsMenuUiState: Flow<OuterPathsUiState> = outerPathsMenuUiStateFlow
    val observeNewPathsInfoList: Flow<NewPathInfoListEvent> = newPathsInfoListFlow

    private var loadPathsJob: Job? = null
    private var downloadAllPathsInfoJob: Job? = null

    private var selectedPathIdsInMenuList: MutableList<Long> = ArrayList()

    private val shownPathIdsByMenu: Map<PathsMenuType, MutableSet<Long>> = mapOf(
        PathsMenuType.MY_PATHS to mutableSetOf(),
        PathsMenuType.OUTER_PATHS to mutableSetOf(),
    )

    private val focusedPathByMenu: MutableMap<PathsMenuType, Long?> = mutableMapOf(
        PathsMenuType.MY_PATHS to null,
        PathsMenuType.OUTER_PATHS to null,
    )

    private var currentBottomMenuState: BottomMenuState? = null

    private fun markPathShown(type: PathsMenuType, ids: Collection<Long>) {
        shownPathIdsByMenu[type]?.addAll(ids)
    }

    private fun markPathHidden(type: PathsMenuType, ids: Collection<Long>) {
        shownPathIdsByMenu[type]?.removeAll(ids.toSet())
    }

    private fun isPathShown(type: PathsMenuType, pathId: Long): Boolean =
        shownPathIdsByMenu[type]?.contains(pathId) == true

    private fun emitListItemFocus(type: PathsMenuType, pathId: Long, isFocused: Boolean) {
        newPathInfoListItemStateFlow.tryEmit(
            NewPathInfoItemState(
                type,
                PathInfoItemState(
                    pathsToAction = PathsToAction.Single(pathId),
                    isFocused = isFocused,
                ),
            )
        )
    }

    private fun setFocusedPath(type: PathsMenuType, newId: Long?) {
        val oldId = focusedPathByMenu[type]
        if (oldId == newId) return
        focusedPathByMenu[type] = newId
        if (oldId != null) emitListItemFocus(type, oldId, isFocused = false)
        if (newId != null) emitListItemFocus(type, newId, isFocused = true)
        newMapEventChannel.trySend(MapEvent.SetFocusedPath(newId))
    }

    private fun clearFocusIfMatches(type: PathsMenuType, id: Long) {
        if (focusedPathByMenu[type] == id) setFocusedPath(type, null)
    }

    private fun clearFocusIfAnyOf(type: PathsMenuType, ids: Collection<Long>) {
        val focused = focusedPathByMenu[type]
        if (focused != null && focused in ids) setFocusedPath(type, null)
    }

    private fun emitBottomMenuStateChange(newState: BottomMenuState) {
        currentBottomMenuState = newState
        val leavingMyPaths = focusedPathByMenu[PathsMenuType.MY_PATHS] != null &&
            newState != BottomMenuState.MY_PATHS_MENU
        val leavingOuter = focusedPathByMenu[PathsMenuType.OUTER_PATHS] != null &&
            newState != BottomMenuState.OUTER_PATHS_MENU
        if (leavingMyPaths) setFocusedPath(PathsMenuType.MY_PATHS, null)
        if (leavingOuter) setFocusedPath(PathsMenuType.OUTER_PATHS, null)
        newMapEventChannel.trySend(MapEvent.BottomMenuStateChange(newState))
    }

    private fun updateMyPathsMenuState(
        showPathsButtonState: ShowPathsButtonState? = null,
        showPathsFilterButtonState: ShowPathsFilterButtonState? = null,
        pathsInfoListState: MyPathsInfoListState? = null,
        inSelectMode: Boolean? = null
    ) {
        myPathsMenuUiStateFlow.update { uiState ->
            uiState.copy(
                showPathsButtonState = showPathsButtonState ?: uiState.showPathsButtonState,
                showPathsFilterButtonState = showPathsFilterButtonState ?: uiState.showPathsFilterButtonState,
                pathsInfoListState = pathsInfoListState ?: uiState.pathsInfoListState,
                inSelectMode = inSelectMode ?: uiState.inSelectMode
            )
        }
    }

    private fun updateOuterPathsMenuState(
        showPathsButtonState: ShowPathsButtonState? = null,
        outerPathsInfoListState: OuterPathsInfoListState? = null,
        inSelectMode: Boolean? = null
    ) {
        outerPathsMenuUiStateFlow.update { uiState ->
            uiState.copy(
                showPathsButtonState = showPathsButtonState ?: uiState.showPathsButtonState,
                outerPathsInfoListState = outerPathsInfoListState ?: uiState.outerPathsInfoListState,
                inSelectMode = inSelectMode ?: uiState.inSelectMode
            )
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
        newPathInfoListItemStateFlow.tryEmit(
            NewPathInfoItemState(
                PathsMenuType.MY_PATHS,
                PathInfoItemState(
                    PathsToAction.Single(pathId),
                    shareButtonState = PathInfoItemShareButtonState.LOADING
                )
            )
        )

        viewModelScope.launch {
            val path = mapPathsInteractor.getSavedRatingPath(pathId, withRatingOnly = false, isOptimized = false)
            if (path != null) {
                try {
                    shareFileChannel.trySend(pathsSaverRepository.saveRatingPath(path))
                } catch (exception: IOException) {
                    // TODO show toast error
                    Log.w(TAG, exception)
                } finally {
                    newPathInfoListItemStateFlow.tryEmit(
                        NewPathInfoItemState(
                            PathsMenuType.MY_PATHS,
                            PathInfoItemState(
                                PathsToAction.Single(pathId),
                                shareButtonState = PathInfoItemShareButtonState.DEFAULT
                            )
                        )
                    )
                }
            }
        }
    }

    fun onShowSelectedPathsButtonClicked(pathsMenuType: PathsMenuType) {
        when (pathsMenuType) {
            PathsMenuType.MY_PATHS -> onShowSelectedPathsButtonClickedMyPathsMenu()
            PathsMenuType.OUTER_PATHS -> onShowSelectedPathsButtonClickedOuterPathsMenu()
        }
    }

    private fun onShowSelectedPathsButtonClickedMyPathsMenu() {
        when (myPathsMenuUiStateFlow.value.showPathsButtonState) {
            ShowPathsButtonState.LOADING -> {
                loadPathsJob?.cancel()
                loadPathsJob = null
                updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
                shownPathIdsByMenu[PathsMenuType.MY_PATHS]?.clear()
                newPathInfoListItemStateFlow.tryEmit(
                    NewPathInfoItemState(
                        PathsMenuType.MY_PATHS,
                        PathInfoItemState(
                            PathsToAction.All,
                            PathInfoItemShowButtonState.DEFAULT
                        )
                    )
                )
            }

            ShowPathsButtonState.DEFAULT -> {
                val selectedPathIds = selectedPathIdsInMenuList.toList()
                if (selectedPathIds.isEmpty()) {
                    newMapEventChannel.trySend(MapEvent.ClearMap)
                    shownPathIdsByMenu[PathsMenuType.MY_PATHS]?.clear()
                    setFocusedPath(PathsMenuType.MY_PATHS, null)
                }

                updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.LOADING)
                newPathInfoListItemStateFlow.tryEmit(
                    NewPathInfoItemState(
                        PathsMenuType.MY_PATHS,
                        PathInfoItemState(
                            PathsToAction.Multiple(selectedPathIds),
                            PathInfoItemShowButtonState.LOADING
                        )
                    )
                )
//                backgroundCachingRatingPathsJob?.cancel()

                when (myPathsMenuUiStateFlow.value.showPathsFilterButtonState) {
                    ShowPathsFilterButtonState.RATED_ONLY -> loadAndShowSelectedRatedPaths()
                    ShowPathsFilterButtonState.ALL_IN_COMMON_COLOR -> loadAndShowSelectedPathsAsCommon()
                    else -> {
                        updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.HIDE)
                    }
                }
            }

            ShowPathsButtonState.HIDE -> {
                hideSelectedPaths(PathsMenuType.MY_PATHS)
            }

            else -> {}
        }
    }

    private fun hideSelectedPaths(pathsMenuType: PathsMenuType) {
        when (pathsMenuType) {
            PathsMenuType.MY_PATHS -> updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
            PathsMenuType.OUTER_PATHS -> updateOuterPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
        }

        val selectedPathIds = selectedPathIdsInMenuList.toList()

        val pathsToHide = if (selectedPathIds.isEmpty()) {
            PathsToAction.All
        } else {
            PathsToAction.Multiple(selectedPathIds)
        }

        when (pathsToHide) {
            PathsToAction.All -> clearFocusIfAnyOf(
                pathsMenuType,
                shownPathIdsByMenu[pathsMenuType] ?: emptySet(),
            )
            is PathsToAction.Multiple -> clearFocusIfAnyOf(pathsMenuType, pathsToHide.pathIds)
            is PathsToAction.Single -> clearFocusIfMatches(pathsMenuType, pathsToHide.pathId)
        }

        when (pathsToHide) {
            PathsToAction.All -> shownPathIdsByMenu[pathsMenuType]?.clear()
            is PathsToAction.Multiple -> markPathHidden(pathsMenuType, pathsToHide.pathIds)
            is PathsToAction.Single -> markPathHidden(pathsMenuType, listOf(pathsToHide.pathId))
        }

        val hideMapEvent = if (pathsToHide == PathsToAction.All) {
            MapEvent.ClearMap
        } else {
            MapEvent.HidePath(pathsToHide)
        }

        newMapEventChannel.trySend(hideMapEvent)
        newPathInfoListItemStateFlow.tryEmit(
            NewPathInfoItemState(
                pathsMenuType,
                PathInfoItemState(
                    pathsToHide,
                    PathInfoItemShowButtonState.DEFAULT
                )
            )
        )
    }

    private fun onShowSelectedPathsButtonClickedOuterPathsMenu() {
        when (outerPathsMenuUiStateFlow.value.showPathsButtonState) {
            ShowPathsButtonState.LOADING -> {
                updateOuterPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
                shownPathIdsByMenu[PathsMenuType.OUTER_PATHS]?.clear()
                newPathInfoListItemStateFlow.tryEmit(
                    NewPathInfoItemState(
                        PathsMenuType.OUTER_PATHS,
                        PathInfoItemState(
                            PathsToAction.All,
                            PathInfoItemShowButtonState.DEFAULT
                        )
                    )
                )
            }

            ShowPathsButtonState.DEFAULT -> {
                if (selectedPathIdsInMenuList.isEmpty()) {
                    newMapEventChannel.trySend(MapEvent.ClearMap)
                }

                updateOuterPathsMenuState(showPathsButtonState = ShowPathsButtonState.HIDE)

                val outerPathsToShow = if (selectedPathIdsInMenuList.isEmpty()) {
                    outerPathsInteractor.getCachedOuterPaths()
                } else {
                    outerPathsInteractor.getCachedOuterPaths().filter { selectedPathIdsInMenuList.contains(it.pathId) }
                }

                newMapEventChannel.trySend(MapEvent.ShowRatingPathList(outerPathsToShow))
                markPathShown(PathsMenuType.OUTER_PATHS, outerPathsToShow.map { it.pathId })
                newPathInfoListItemStateFlow.tryEmit(
                    NewPathInfoItemState(
                        PathsMenuType.OUTER_PATHS,
                        PathInfoItemState(
                            PathsToAction.Multiple(outerPathsToShow.map { it.pathId }),
                            PathInfoItemShowButtonState.HIDE
                        )
                    )
                )
            }

            ShowPathsButtonState.HIDE -> {
                hideSelectedPaths(PathsMenuType.OUTER_PATHS)
            }

            else -> {}
        }
    }

    private fun loadAndShowSelectedRatedPaths() {
        val selectedPathsIds = selectedPathIdsInMenuList.toList()
        if (selectedPathsIds.isEmpty()) {
            loadAndShowAllRatedPaths()
        } else {
            loadPathsJob?.cancel()
            loadPathsJob = viewModelScope.launch {
                val loadedPaths = ArrayList<MapRatingPath>()
                for (pathId in selectedPathsIds) {
                    val ratingPath = mapPathsInteractor.getSavedRatingPath(pathId, withRatingOnly = true, isOptimized = true)
                    if (ratingPath != null) {
                        loadedPaths.add(ratingPath)
                    }
                }
                if (loadedPaths.isNotEmpty()) {
                    newMapEventChannel.trySend(MapEvent.ShowRatingPathList(loadedPaths))
                    markPathShown(PathsMenuType.MY_PATHS, loadedPaths.map { it.pathId })
                    analyticsTracker.track(AnalyticsEvent.PathShown(loadedPaths.size, AnalyticsPathType.RATING))
                    newPathInfoListItemStateFlow.tryEmit(
                        NewPathInfoItemState(
                            PathsMenuType.MY_PATHS,
                            PathInfoItemState(
                                PathsToAction.Multiple(selectedPathsIds),
                                PathInfoItemShowButtonState.HIDE
                            )
                        )
                    )
                    updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.HIDE)
                } else {
                    updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
                }
            }
        }
    }

    private fun loadAndShowSelectedPathsAsCommon() {
        val selectedPathsIds = selectedPathIdsInMenuList.toList()
        if (selectedPathsIds.isEmpty()) {
            loadAndShowAllPathsAsCommon()
        } else {
            loadPathsJob?.cancel()
            loadPathsJob = viewModelScope.launch {
                val loadedPaths = ArrayList<MapCommonPath>()
                for (pathId in selectedPathsIds) {
                    val commonPath = mapPathsInteractor.getSavedCommonPath(pathId, true)
                    if (commonPath != null) {
                        loadedPaths.add(commonPath)
                    }
                }
                if (loadedPaths.isNotEmpty()) {
                    newMapEventChannel.trySend(MapEvent.ShowCommonPathList(loadedPaths))
                    markPathShown(PathsMenuType.MY_PATHS, loadedPaths.map { it.pathId })
                    analyticsTracker.track(AnalyticsEvent.PathShown(loadedPaths.size, AnalyticsPathType.COMMON))
                    newPathInfoListItemStateFlow.tryEmit(
                        NewPathInfoItemState(
                            PathsMenuType.MY_PATHS,
                            PathInfoItemState(
                                PathsToAction.Multiple(selectedPathsIds),
                                PathInfoItemShowButtonState.HIDE
                            )
                        )
                    )
                    updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.HIDE)
                } else {
                    updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
                }
            }
        }
    }

    private fun loadAndShowAllRatedPaths() {
        loadPathsJob?.cancel()
        loadPathsJob = viewModelScope.launch {
            val allPaths = mapPathsInteractor.getAllSavedRatingPaths(true)
            for (path in allPaths) {
                newMapEventChannel.trySend(MapEvent.ShowRatingPath(path))
                markPathShown(PathsMenuType.MY_PATHS, listOf(path.pathId))
                newPathInfoListItemStateFlow.tryEmit(
                    NewPathInfoItemState(
                        PathsMenuType.MY_PATHS,
                        PathInfoItemState(
                            PathsToAction.Single(path.pathId),
                            PathInfoItemShowButtonState.HIDE
                        )
                    )
                )
            }
            if (allPaths.isNotEmpty()) {
                analyticsTracker.track(AnalyticsEvent.PathShown(allPaths.size, AnalyticsPathType.RATING))
            }
            updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.HIDE)
        }
    }

    private fun loadAndShowAllPathsAsCommon() {
        loadPathsJob?.cancel()
        loadPathsJob = viewModelScope.launch {
            val allPaths = mapPathsInteractor.getAllSavedPathsAsCommon()
            for (path in allPaths) {
                newMapEventChannel.trySend(MapEvent.ShowCommonPath(path))
                markPathShown(PathsMenuType.MY_PATHS, listOf(path.pathId))
                newPathInfoListItemStateFlow.tryEmit(
                    NewPathInfoItemState(
                        PathsMenuType.MY_PATHS,
                        PathInfoItemState(
                            PathsToAction.Single(path.pathId),
                            PathInfoItemShowButtonState.HIDE
                        )
                    )
                )
            }
            if (allPaths.isNotEmpty()) {
                analyticsTracker.track(AnalyticsEvent.PathShown(allPaths.size, AnalyticsPathType.COMMON))
            }
            updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.HIDE)
        }
    }

    fun onShowPathsFilterButtonClicked() {
        val newFilterValue = when (myPathsMenuUiStateFlow.value.showPathsFilterButtonState) {
            ShowPathsFilterButtonState.RATED_ONLY -> ShowPathsFilterButtonState.ALL_IN_COMMON_COLOR
            ShowPathsFilterButtonState.ALL_IN_COMMON_COLOR -> ShowPathsFilterButtonState.RATED_ONLY
            ShowPathsFilterButtonState.GONE -> ShowPathsFilterButtonState.GONE
        }

        updateMyPathsMenuState(showPathsFilterButtonState = newFilterValue)

        when (myPathsMenuUiStateFlow.value.showPathsButtonState) {
            ShowPathsButtonState.LOADING -> {
                when (newFilterValue) {
                    ShowPathsFilterButtonState.RATED_ONLY -> loadAndShowSelectedRatedPaths()
                    ShowPathsFilterButtonState.ALL_IN_COMMON_COLOR -> loadAndShowSelectedPathsAsCommon()
                    else -> {
                        updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.HIDE)
                    }
                }
            }

            ShowPathsButtonState.HIDE -> {
                updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.DEFAULT)
            }

            else -> {}
        }
    }

    fun onShowPathsMenuButtonClick() {
        loadPathsJob?.cancel()
        selectedPathIdsInMenuList.clear()

        emitBottomMenuStateChange(BottomMenuState.MY_PATHS_MENU)
        updateMyPathsMenuState(inSelectMode = false, pathsInfoListState = MyPathsInfoListState.LOADING)

        downloadAllPathsInfoJob = viewModelScope.launch {
            val allSavedPathsList = removeDeletingNowPathsFromList(mapPathsInteractor.getAllSavedPathsInfo())

            if (allSavedPathsList.isNotEmpty()) {
                newPathsInfoListFlow.tryEmit(NewPathInfoListEvent(PathsMenuType.MY_PATHS, allSavedPathsList))
                updateMyPathsMenuState(
                    pathsInfoListState = MyPathsInfoListState.DEFAULT,
                    showPathsButtonState = ShowPathsButtonState.DEFAULT,
                    showPathsFilterButtonState = ShowPathsFilterButtonState.RATED_ONLY
                )
            } else {
                updateMyPathsMenuState(
                    pathsInfoListState = MyPathsInfoListState.EMPTY_LIST,
                    showPathsButtonState = ShowPathsButtonState.GONE,
                    showPathsFilterButtonState = ShowPathsFilterButtonState.GONE,
                    inSelectMode = false
                )
            }
        }
    }

    private fun removeDeletingNowPathsFromList(pathsList: List<MapPathInfo>): List<MapPathInfo> {
        val deletingPaths = pathRedactor.getDeletingNowPathsIds()
        return pathsList.filter { path -> !deletingPaths.contains(path.pathId) }
    }

    fun onPathsMenuBackButtonClicked() {
        loadPathsJob?.cancel()
        selectedPathIdsInMenuList.clear()

        updateMyPathsMenuState(
            showPathsButtonState = ShowPathsButtonState.GONE,
            showPathsFilterButtonState = ShowPathsFilterButtonState.GONE
        )

        emitBottomMenuStateChange(BottomMenuState.DEFAULT)
    }

    fun onPathInListButtonClicked(
        pathId: Long,
        clickedButtonType: PathItemButtonType,
        pathsMenuType: PathsMenuType
    ) {
        when (pathsMenuType) {
            PathsMenuType.MY_PATHS -> onPathInMyListButtonClicked(pathId, clickedButtonType)
            PathsMenuType.OUTER_PATHS -> onPathInOuterListButtonClicked(pathId, clickedButtonType)
        }
    }

    private fun onPathInMyListButtonClicked(
        pathId: Long,
        clickedButtonType: PathItemButtonType
    ) {
        when (clickedButtonType) {
            is PathItemButtonType.Show -> {
                when (clickedButtonType.currentState) {
                    PathInfoItemShowButtonState.LOADING, PathInfoItemShowButtonState.HIDE -> {
                        clearFocusIfMatches(PathsMenuType.MY_PATHS, pathId)
                        markPathHidden(PathsMenuType.MY_PATHS, listOf(pathId))
                        newMapEventChannel.trySend(MapEvent.HidePath(PathsToAction.Single(pathId)))
                        newPathInfoListItemStateFlow.tryEmit(
                            NewPathInfoItemState(
                                PathsMenuType.MY_PATHS,
                                PathInfoItemState(
                                    PathsToAction.Single(pathId),
                                    PathInfoItemShowButtonState.DEFAULT
                                )
                            )
                        )
                    }

                    PathInfoItemShowButtonState.DEFAULT -> {
                        newPathInfoListItemStateFlow.tryEmit(
                            NewPathInfoItemState(
                                PathsMenuType.MY_PATHS,
                                PathInfoItemState(
                                    PathsToAction.Single(pathId),
                                    PathInfoItemShowButtonState.LOADING
                                )
                            )
                        )
                        viewModelScope.launch {
                            val savedRatingPath = mapPathsInteractor.getSavedRatingPath(
                                pathId,
                                withRatingOnly = false,
                                isOptimized = true
                            )
                            if (savedRatingPath != null) {
                                markPathShown(PathsMenuType.MY_PATHS, listOf(pathId))
                                newMapEventChannel.trySend(MapEvent.ShowRatingPath(savedRatingPath))
                                newPathInfoListItemStateFlow.tryEmit(
                                    NewPathInfoItemState(
                                        PathsMenuType.MY_PATHS,
                                        PathInfoItemState(
                                            PathsToAction.Single(pathId),
                                            PathInfoItemShowButtonState.HIDE
                                        )
                                    )
                                )
                            } else {
                                // TODO handle bd error
                            }
                        }
                    }
                }
            }

            PathItemButtonType.Delete -> {
                newConfirmDialogChannel.trySend(
                    ConfirmDialogType.DeletePath(pathId)
                )
            }

            PathItemButtonType.Share -> {
                checkPermissionsAndSharePath(pathId)
            }
        }
    }

    private fun onPathInOuterListButtonClicked(
        tempPathId: Long,
        clickedButtonType: PathItemButtonType
    ) {
        when (clickedButtonType) {
            is PathItemButtonType.Show -> {
                when (clickedButtonType.currentState) {
                    PathInfoItemShowButtonState.LOADING, PathInfoItemShowButtonState.HIDE -> {
                        clearFocusIfMatches(PathsMenuType.OUTER_PATHS, tempPathId)
                        markPathHidden(PathsMenuType.OUTER_PATHS, listOf(tempPathId))
                        newMapEventChannel.trySend(MapEvent.HidePath(PathsToAction.Single(tempPathId)))
                        newPathInfoListItemStateFlow.tryEmit(
                            NewPathInfoItemState(
                                PathsMenuType.OUTER_PATHS,
                                PathInfoItemState(
                                    PathsToAction.Single(tempPathId),
                                    PathInfoItemShowButtonState.DEFAULT
                                )
                            )
                        )
                    }

                    PathInfoItemShowButtonState.DEFAULT -> {
                        val cachedPath = outerPathsInteractor.getCachedOuterPath(tempPathId)
                        if (cachedPath != null) {
                            markPathShown(PathsMenuType.OUTER_PATHS, listOf(tempPathId))
                            newPathInfoListItemStateFlow.tryEmit(
                                NewPathInfoItemState(
                                    PathsMenuType.OUTER_PATHS,
                                    PathInfoItemState(
                                        PathsToAction.Single(tempPathId),
                                        PathInfoItemShowButtonState.HIDE
                                    )
                                )
                            )
                            newMapEventChannel.trySend(MapEvent.ShowRatingPath(cachedPath))
                        } else {
                            deleteOuterPathFromList(tempPathId)
                        }
                    }
                }
            }

            PathItemButtonType.Delete -> {
                deleteOuterPathFromList(tempPathId)
            }

            else -> {}
        }
    }

    fun onPathInListShortTap(
        pathId: Long,
        pathsMenuType: PathsMenuType
    ) {
        val inSelectMode = when (pathsMenuType) {
            PathsMenuType.MY_PATHS -> myPathsMenuUiStateFlow.value.inSelectMode
            PathsMenuType.OUTER_PATHS -> outerPathsMenuUiStateFlow.value.inSelectMode
        }
        if (inSelectMode) {
            toggleMenuItemSelect(pathId, pathsMenuType)
            return
        }
        if (!isPathShown(pathsMenuType, pathId)) {
            return
        }

        val currentFocused = focusedPathByMenu[pathsMenuType]
        if (currentFocused == pathId) {
            // toggle off — no camera move
            setFocusedPath(pathsMenuType, null)
            return
        }

        setFocusedPath(pathsMenuType, pathId)

        viewModelScope.launch {
            val path = resolveShownPath(pathId, pathsMenuType) ?: return@launch
            val bounds = pathBounds(path) ?: return@launch
            newMapEventChannel.trySend(MapEvent.FitCameraToBounds(bounds))
        }
    }

    private suspend fun resolveShownPath(
        pathId: Long,
        pathsMenuType: PathsMenuType,
    ): MapRatingPath? = when (pathsMenuType) {
        PathsMenuType.OUTER_PATHS -> outerPathsInteractor.getCachedOuterPath(pathId)
        PathsMenuType.MY_PATHS -> mapPathsInteractor.getSavedRatingPath(
            pathId,
            withRatingOnly = false,
            isOptimized = true,
        )
    }

    fun onPathInListLongTap(
        pathId: Long,
        pathsMenuType: PathsMenuType
    ) {
        setFocusedPath(pathsMenuType, null)
        toggleMenuItemSelect(pathId, pathsMenuType)
    }

    fun onPathTappedOnMap(pathId: Long) {
        val menuType = activeMenuTypeForFocus() ?: return
        if (!isPathShown(menuType, pathId)) return

        val currentFocused = focusedPathByMenu[menuType]
        if (currentFocused == pathId) {
            // toggle off — no scroll
            setFocusedPath(menuType, null)
            return
        }

        setFocusedPath(menuType, pathId)
        newMapEventChannel.trySend(MapEvent.ScrollListToPath(menuType, pathId))
    }

    private fun activeMenuTypeForFocus(): PathsMenuType? = when (currentBottomMenuState) {
        BottomMenuState.MY_PATHS_MENU -> PathsMenuType.MY_PATHS
        BottomMenuState.OUTER_PATHS_MENU -> PathsMenuType.OUTER_PATHS
        BottomMenuState.DEFAULT -> null
        null -> null
    }

    private fun toggleMenuItemSelect(pathId: Long, pathsMenuType: PathsMenuType) {
        val isItemSelected = if (selectedPathIdsInMenuList.contains(pathId)) {
            selectedPathIdsInMenuList.remove(pathId)
            false
        } else {
            selectedPathIdsInMenuList.add(pathId)
            true
        }

        syncMenuSelectMode(pathsMenuType)

        newPathInfoListItemStateFlow.tryEmit(
            NewPathInfoItemState(
                pathsMenuType,
                PathInfoItemState(
                    PathsToAction.Single(pathId),
                    isSelected = isItemSelected
                )
            )
        )
    }

    private fun syncMenuSelectMode(pathsMenuType: PathsMenuType) {
        when (pathsMenuType) {
            PathsMenuType.MY_PATHS -> updateMyPathsMenuState(inSelectMode = selectedPathIdsInMenuList.isNotEmpty())
            PathsMenuType.OUTER_PATHS -> updateOuterPathsMenuState(
                inSelectMode = selectedPathIdsInMenuList.isNotEmpty()
            )
        }
    }

    fun onConfirmMyPathDelete(pathId: Long) {
        clearFocusIfMatches(PathsMenuType.MY_PATHS, pathId)
        markPathHidden(PathsMenuType.MY_PATHS, listOf(pathId))
        MainScope().launch {
            pathRedactor.deletePath(pathId)
            checkSavedPathsListNotEmptyNow()
        }
        newMapEventChannel.trySend(MapEvent.HidePath(PathsToAction.Single(pathId)))
        deletePathInfoItemChannel.trySend(DeletePathInfoItemEvent(PathsMenuType.MY_PATHS, PathsToAction.Single(pathId)))
        selectedPathIdsInMenuList.remove(pathId)
        checkStillInSelectedMode(PathsMenuType.MY_PATHS)
        viewModelScope.launch { checkSavedPathsListNotEmptyNow(listOf(pathId)) }
    }

    fun onConfirmMyPathListDelete(pathIds: List<Long>) {
        clearFocusIfAnyOf(PathsMenuType.MY_PATHS, pathIds)
        markPathHidden(PathsMenuType.MY_PATHS, pathIds)
        MainScope().launch {
            pathRedactor.deletePaths(pathIds)
            checkSavedPathsListNotEmptyNow()
        }
        newMapEventChannel.trySend(MapEvent.HidePath(PathsToAction.Multiple(pathIds)))
        deletePathInfoItemChannel.trySend(
            DeletePathInfoItemEvent(PathsMenuType.MY_PATHS, PathsToAction.Multiple(pathIds))
        )
        selectedPathIdsInMenuList.removeAll(pathIds)
        checkStillInSelectedMode(PathsMenuType.MY_PATHS)
        viewModelScope.launch { checkSavedPathsListNotEmptyNow(pathIds) }
    }

    private fun deleteOuterPathFromList(tempPathId: Long) {
        clearFocusIfMatches(PathsMenuType.OUTER_PATHS, tempPathId)
        markPathHidden(PathsMenuType.OUTER_PATHS, listOf(tempPathId))
        outerPathsInteractor.removeCachedPath(tempPathId)
        newMapEventChannel.trySend(MapEvent.HidePath(PathsToAction.Single(tempPathId)))
        deletePathInfoItemChannel.trySend(
            DeletePathInfoItemEvent(PathsMenuType.OUTER_PATHS, PathsToAction.Single(tempPathId))
        )

        if (outerPathsInteractor.getCachedOuterPaths().isEmpty()) {
            updateOuterPathsMenuState(
                showPathsButtonState = ShowPathsButtonState.GONE,
                outerPathsInfoListState = OuterPathsInfoListState.EMPTY_LIST
            )
        }
        selectedPathIdsInMenuList.remove(tempPathId)
        checkStillInSelectedMode(PathsMenuType.OUTER_PATHS)
    }

    private fun deleteSelectedOuterPathsFromList() {
        val selectedPathIds = selectedPathIdsInMenuList.toList()
        clearFocusIfAnyOf(PathsMenuType.OUTER_PATHS, selectedPathIds)
        markPathHidden(PathsMenuType.OUTER_PATHS, selectedPathIds)

        for (pathId in selectedPathIds) {
            outerPathsInteractor.removeCachedPath(pathId)
        }
        newMapEventChannel.trySend(MapEvent.HidePath(PathsToAction.Multiple(selectedPathIds)))
        deletePathInfoItemChannel.trySend(
            DeletePathInfoItemEvent(PathsMenuType.OUTER_PATHS, PathsToAction.Multiple(selectedPathIds))
        )

        if (outerPathsInteractor.getCachedOuterPaths().isEmpty()) {
            updateOuterPathsMenuState(
                showPathsButtonState = ShowPathsButtonState.GONE,
                outerPathsInfoListState = OuterPathsInfoListState.EMPTY_LIST
            )
        }
        selectedPathIdsInMenuList.removeAll(selectedPathIds)
        checkStillInSelectedMode(PathsMenuType.OUTER_PATHS)
    }

    private suspend fun checkSavedPathsListNotEmptyNow() {
        if (mapPathsInteractor.getAllSavedPathsInfo().isEmpty()) {
            updateMyPathsMenuState(
                pathsInfoListState = MyPathsInfoListState.EMPTY_LIST,
                showPathsButtonState = ShowPathsButtonState.GONE,
                showPathsFilterButtonState = ShowPathsFilterButtonState.GONE,
                inSelectMode = false
            )
        }
    }

    private suspend fun checkSavedPathsListNotEmptyNow(deletingPathsIds: List<Long>) {
        val allMapPathsInfo = mapPathsInteractor.getAllSavedPathsInfo()
        if (allMapPathsInfo.isEmpty() || allMapPathsInfo.map { it.pathId } == deletingPathsIds) {
            updateMyPathsMenuState(
                pathsInfoListState = MyPathsInfoListState.EMPTY_LIST,
                showPathsButtonState = ShowPathsButtonState.GONE,
                showPathsFilterButtonState = ShowPathsFilterButtonState.GONE,
                inSelectMode = false
            )
        }
    }

    private fun checkStillInSelectedMode(pathsMenuType: PathsMenuType) {
        if (selectedPathIdsInMenuList.isEmpty()) {
            when (pathsMenuType) {
                PathsMenuType.MY_PATHS -> updateMyPathsMenuState(inSelectMode = false)
                PathsMenuType.OUTER_PATHS -> updateOuterPathsMenuState(inSelectMode = false)
            }
        }
    }

    private fun loadAndShowSharedPaths(sharedFileUri: Uri) {
        selectedPathIdsInMenuList.clear()

        updateOuterPathsMenuState(outerPathsInfoListState = OuterPathsInfoListState.LOADING, inSelectMode = false)

        emitBottomMenuStateChange(BottomMenuState.OUTER_PATHS_MENU)

        viewModelScope.launch {
            val outerPathsInfo = outerPathsInteractor.getAllPaths(sharedFileUri)
            if (outerPathsInfo.isNotEmpty()) {
                newPathsInfoListFlow.tryEmit(NewPathInfoListEvent(PathsMenuType.OUTER_PATHS, outerPathsInfo))

                updateOuterPathsMenuState(
                    showPathsButtonState = ShowPathsButtonState.DEFAULT,
                    outerPathsInfoListState = OuterPathsInfoListState.DEFAULT
                )
            } else {
                updateOuterPathsMenuState(
                    outerPathsInfoListState = OuterPathsInfoListState.EMPTY_LIST
                )
            }
        }
    }

    fun onOuterPathsConfirmButtonClicked() {
        selectedPathIdsInMenuList.clear()

        viewModelScope.launch {
            val importedCount = outerPathsInteractor.getCachedOuterPaths().size
            outerPathsInteractor.saveCachedPaths()
            if (importedCount > 0) {
                analyticsTracker.track(AnalyticsEvent.OuterPathImported(importedCount))
            }
        }

        updateOuterPathsMenuState(
            showPathsButtonState = ShowPathsButtonState.GONE,
            outerPathsInfoListState = OuterPathsInfoListState.DEFAULT,
            inSelectMode = false
        )

        emitBottomMenuStateChange(BottomMenuState.DEFAULT)
    }

    fun onSelectAllPathsButtonClicked(pathsMenuType: PathsMenuType, pathsIdsInList: List<Long>) {
        selectedPathIdsInMenuList.clear()
        selectedPathIdsInMenuList.addAll(pathsIdsInList)
        newPathInfoListItemStateFlow.tryEmit(
            NewPathInfoItemState(
                pathsMenuType,
                PathInfoItemState(
                    PathsToAction.All,
                    isSelected = true
                )
            )
        )
    }

    fun onShareSelectedPathsButtonClicked(pathsMenuType: PathsMenuType) {
        val selectedPathIds = selectedPathIdsInMenuList.toList()
        newPathInfoListItemStateFlow.tryEmit(
            NewPathInfoItemState(
                pathsMenuType,
                PathInfoItemState(
                    PathsToAction.Multiple(selectedPathIds),
                    shareButtonState = PathInfoItemShareButtonState.LOADING
                )
            )
        )

        viewModelScope.launch {
            val selectedPaths = ArrayList<MapRatingPath>()

            for (pathId in selectedPathIds) {
                val path = mapPathsInteractor.getSavedRatingPath(pathId, withRatingOnly = false, isOptimized = false)
                if (path != null) {
                    selectedPaths.add(path)
                }
            }

            if (selectedPaths.isNotEmpty()) {
                try {
                    shareFileChannel.trySend(pathsSaverRepository.saveRatingPathList(selectedPaths))
                    analyticsTracker.track(AnalyticsEvent.TrackShared(selectedPaths.size))
                } catch (exception: IOException) {
                    // TODO show toast error
                    Log.w(TAG, exception)
                }
            } else {
                // TODO show toast error
            }

            newPathInfoListItemStateFlow.tryEmit(
                NewPathInfoItemState(
                    pathsMenuType,
                    PathInfoItemState(
                        PathsToAction.Multiple(selectedPathIds),
                        shareButtonState = PathInfoItemShareButtonState.DEFAULT
                    )
                )
            )
        }
    }

    fun onDeleteSelectedPathsButtonClicked(pathsMenuType: PathsMenuType) {
        when (pathsMenuType) {
            PathsMenuType.MY_PATHS -> {
                newConfirmDialogChannel.trySend(
                    ConfirmDialogType.DeleteMultiplePaths(selectedPathIdsInMenuList.toList())
                )
            }

            PathsMenuType.OUTER_PATHS -> {
                deleteSelectedOuterPathsFromList()
            }
        }
    }

    fun onResume(extraData: Uri?) {
        if (extraData != null) {
            loadAndShowSharedPaths(extraData)
        }
    }

    companion object {
        private val TAG = PathsMenuViewModel::class.java.canonicalName
    }
}
