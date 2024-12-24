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
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathsSaverRepository
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
    private val pathRedactor: IPathRedactor
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
    private val newMapEventChannel = Channel<MapEvent>()

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
            for (path in mapPathsInteractor.getAllSavedRatingPaths(true)) {
                newMapEventChannel.trySend(MapEvent.ShowRatingPath(path))
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
            updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.HIDE)
        }
    }

    private fun loadAndShowAllPathsAsCommon() {
        loadPathsJob?.cancel()
        loadPathsJob = viewModelScope.launch {
            for (path in mapPathsInteractor.getAllSavedPathsAsCommon()) {
                newMapEventChannel.trySend(MapEvent.ShowCommonPath(path))
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

        newMapEventChannel.trySend(MapEvent.BottomMenuStateChange(BottomMenuState.MY_PATHS_MENU))
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

        newMapEventChannel.trySend(MapEvent.BottomMenuStateChange(BottomMenuState.DEFAULT))
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
        if ((pathsMenuType == PathsMenuType.MY_PATHS && !myPathsMenuUiStateFlow.value.inSelectMode) ||
            (pathsMenuType == PathsMenuType.OUTER_PATHS && !outerPathsMenuUiStateFlow.value.inSelectMode)
        ) {
            return // ignore short tap without select mode
        }

        toggleMenuItemSelect(pathId, pathsMenuType)
    }

    fun onPathInListLongTap(
        pathId: Long,
        pathsMenuType: PathsMenuType
    ) {
        toggleMenuItemSelect(pathId, pathsMenuType)
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

        newMapEventChannel.trySend(MapEvent.BottomMenuStateChange(BottomMenuState.OUTER_PATHS_MENU))

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
            outerPathsInteractor.saveCachedPaths()
        }

        updateOuterPathsMenuState(
            showPathsButtonState = ShowPathsButtonState.GONE,
            outerPathsInfoListState = OuterPathsInfoListState.DEFAULT,
            inSelectMode = false
        )

        newMapEventChannel.trySend(MapEvent.BottomMenuStateChange(BottomMenuState.DEFAULT))
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
