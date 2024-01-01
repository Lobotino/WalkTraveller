package ru.lobotino.walktraveller.viewmodels

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathsSaverRepository
import ru.lobotino.walktraveller.ui.model.BottomMenuState
import ru.lobotino.walktraveller.ui.model.ConfirmDialogInfo
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
    private val newConfirmDialogChannel = Channel<ConfirmDialogInfo>()
    private val newMapEventChannel = Channel<MapEvent>()

    val observeShareFileChannel = shareFileChannel.consumeAsFlow()
    val observeDeletePathInfoItemChannel = deletePathInfoItemChannel.consumeAsFlow()
    val observeNewConfirmDialog: Flow<ConfirmDialogInfo> = newConfirmDialogChannel.consumeAsFlow()
    val observeNewMapEvent: Flow<MapEvent> = newMapEventChannel.consumeAsFlow()
    val observeNewPathInfoListItemState: Flow<NewPathInfoItemState> = newPathInfoListItemStateFlow
    val observeMyPathsMenuUiState: Flow<MyPathsUiState> = myPathsMenuUiStateFlow
    val observeOuterPathsMenuUiState: Flow<OuterPathsUiState> = outerPathsMenuUiStateFlow
    val observeNewPathsInfoList: Flow<NewPathInfoListEvent> = newPathsInfoListFlow

    private var downloadAllPathsJob: Job? = null
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
            val path = mapPathsInteractor.getSavedRatingPath(pathId, false)
            if (path != null) {
                try {
                    shareFileChannel.trySend(pathsSaverRepository.saveRatingPath(path))
                } catch (exception: IOException) {
                    //TODO show toast error
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
            PathsMenuType.MY_PATHS -> onShowAllPathsButtonClickedMyPathsMenu()
            PathsMenuType.OUTER_PATHS -> onShowAllPathsButtonClickedOuterPathsMenu()
        }
    }

    private fun onShowAllPathsButtonClickedMyPathsMenu() {
        if (myPathsMenuUiStateFlow.value.showPathsButtonState == ShowPathsButtonState.LOADING) {
            downloadAllPathsJob?.cancel()
            downloadAllPathsJob = null
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
        } else {
            if (myPathsMenuUiStateFlow.value.showPathsButtonState == ShowPathsButtonState.HIDE) {
                newMapEventChannel.trySend(MapEvent.ClearMap)
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
            } else {
                newMapEventChannel.trySend(MapEvent.ClearMap)
                updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.LOADING)
                newPathInfoListItemStateFlow.tryEmit(
                    NewPathInfoItemState(
                        PathsMenuType.MY_PATHS,
                        PathInfoItemState(
                            PathsToAction.All,
                            PathInfoItemShowButtonState.LOADING
                        )
                    )
                )
//                backgroundCachingRatingPathsJob?.cancel()

                when (myPathsMenuUiStateFlow.value.showPathsFilterButtonState) {
                    ShowPathsFilterButtonState.RATED_ONLY -> startDownloadAllRatedPaths()
                    ShowPathsFilterButtonState.ALL_IN_COMMON_COLOR -> startDownloadAllPathsAsCommon()
                    else -> {
                        updateMyPathsMenuState(showPathsButtonState = ShowPathsButtonState.HIDE)
                    }
                }
            }
        }
    }

    private fun onShowAllPathsButtonClickedOuterPathsMenu() {
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
                newMapEventChannel.trySend(MapEvent.ClearMap)
                updateOuterPathsMenuState(showPathsButtonState = ShowPathsButtonState.HIDE)
                newPathInfoListItemStateFlow.tryEmit(
                    NewPathInfoItemState(
                        PathsMenuType.OUTER_PATHS,
                        PathInfoItemState(
                            PathsToAction.All,
                            PathInfoItemShowButtonState.LOADING
                        )
                    )
                )
                for (outerPath in outerPathsInteractor.getCachedOuterPaths()) {
                    newMapEventChannel.trySend(MapEvent.ShowRatingPath(outerPath))
                    newPathInfoListItemStateFlow.tryEmit(
                        NewPathInfoItemState(
                            PathsMenuType.OUTER_PATHS,
                            PathInfoItemState(
                                PathsToAction.Single(outerPath.pathId),
                                PathInfoItemShowButtonState.HIDE
                            )
                        )
                    )
                }
            }

            ShowPathsButtonState.HIDE -> {
                newMapEventChannel.trySend(MapEvent.ClearMap)
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

            else -> {}
        }
    }


    private fun startDownloadAllRatedPaths() {
        downloadAllPathsJob?.cancel()
        downloadAllPathsJob = viewModelScope.launch {
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

    private fun startDownloadAllPathsAsCommon() {
        downloadAllPathsJob?.cancel()
        downloadAllPathsJob = viewModelScope.launch {
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
                    ShowPathsFilterButtonState.RATED_ONLY -> startDownloadAllRatedPaths()
                    ShowPathsFilterButtonState.ALL_IN_COMMON_COLOR -> startDownloadAllPathsAsCommon()
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
        downloadAllPathsJob?.cancel()
        selectedPathIdsInMenuList.clear()

        newMapEventChannel.trySend(MapEvent.BottomMenuStateChange(BottomMenuState.MY_PATHS_MENU))
        updateMyPathsMenuState(inSelectMode = false, pathsInfoListState = MyPathsInfoListState.LOADING)

        downloadAllPathsInfoJob = viewModelScope.launch {
            val allSavedPathsList = mapPathsInteractor.getAllSavedPathsInfo()
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

    fun onPathsMenuBackButtonClicked() {
        downloadAllPathsJob?.cancel()
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
                            val savedRatingPath = mapPathsInteractor.getSavedRatingPath(pathId, false)
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
                                //TODO handle bd error
                            }
                        }
                    }
                }
            }

            PathItemButtonType.Delete -> {
                newConfirmDialogChannel.trySend(
                    ConfirmDialogInfo(
                        ConfirmDialogType.DELETE_PATH,
                        pathId
                    )
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
                        val cachedPath = outerPathsInteractor.getCachedOuterPath(tempPathId.toInt())
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
            return //ignore short tap without select mode
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
            PathsMenuType.OUTER_PATHS -> updateOuterPathsMenuState(inSelectMode = selectedPathIdsInMenuList.isNotEmpty())
        }
    }

    fun onConfirmMyPathDelete(pathId: Long) {
        viewModelScope.launch {
            pathRedactor.deletePath(pathId)
            checkMyPathsListNotEmptyNow()
        }
        newMapEventChannel.trySend(MapEvent.HidePath(PathsToAction.Single(pathId)))
        deletePathInfoItemChannel.trySend(DeletePathInfoItemEvent(PathsMenuType.MY_PATHS, PathsToAction.Single(pathId)))
    }

    fun onConfirmMyPathListDelete(pathIds: List<Long>) {
        viewModelScope.launch {
            for (pathId in pathIds) {
                pathRedactor.deletePath(pathId)
            }
            checkMyPathsListNotEmptyNow()
        }
        newMapEventChannel.trySend(MapEvent.HidePath(PathsToAction.Multiple(pathIds)))
        deletePathInfoItemChannel.trySend(DeletePathInfoItemEvent(PathsMenuType.MY_PATHS, PathsToAction.Multiple(pathIds)))
    }

    private fun deleteOuterPathFromList(tempPathId: Long) {
        outerPathsInteractor.removeCachedPath(tempPathId.toInt())
        newMapEventChannel.trySend(MapEvent.HidePath(PathsToAction.Single(tempPathId)))
        deletePathInfoItemChannel.trySend(DeletePathInfoItemEvent(PathsMenuType.OUTER_PATHS, PathsToAction.Single(tempPathId)))

        if (outerPathsInteractor.getCachedOuterPaths().isEmpty()) {
            updateOuterPathsMenuState(
                showPathsButtonState = ShowPathsButtonState.GONE,
                outerPathsInfoListState = OuterPathsInfoListState.EMPTY_LIST
            )
        }
    }

    private fun deleteSelectedOuterPathsFromList() {
        val selectedPathIds = ArrayList(selectedPathIdsInMenuList)

        for (pathId in selectedPathIds) {
            outerPathsInteractor.removeCachedPath(pathId.toInt())
        }
        newMapEventChannel.trySend(MapEvent.HidePath(PathsToAction.Multiple(selectedPathIds)))
        deletePathInfoItemChannel.trySend(DeletePathInfoItemEvent(PathsMenuType.OUTER_PATHS, PathsToAction.Multiple(selectedPathIds)))

        if (outerPathsInteractor.getCachedOuterPaths().isEmpty()) {
            updateOuterPathsMenuState(
                showPathsButtonState = ShowPathsButtonState.GONE,
                outerPathsInfoListState = OuterPathsInfoListState.EMPTY_LIST
            )
        }
    }

    private suspend fun checkMyPathsListNotEmptyNow() {
        if (mapPathsInteractor.getAllSavedPathsInfo().isEmpty()) {
            updateMyPathsMenuState(
                pathsInfoListState = MyPathsInfoListState.EMPTY_LIST,
                showPathsButtonState = ShowPathsButtonState.GONE,
                showPathsFilterButtonState = ShowPathsFilterButtonState.GONE,
                inSelectMode = false
            )
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
        val selectedPathIdsInMenuList = ArrayList(selectedPathIdsInMenuList)
        newPathInfoListItemStateFlow.tryEmit(
            NewPathInfoItemState(
                pathsMenuType,
                PathInfoItemState(
                    PathsToAction.Multiple(selectedPathIdsInMenuList),
                    shareButtonState = PathInfoItemShareButtonState.LOADING
                )
            )
        )

        viewModelScope.launch {
            val selectedPaths = ArrayList<MapRatingPath>()

            for (pathId in selectedPathIdsInMenuList) {
                val path = mapPathsInteractor.getSavedRatingPath(pathId, false)
                if (path != null) {
                    selectedPaths.add(path)
                }
            }

            if (selectedPaths.isNotEmpty()) {
                try {
                    shareFileChannel.trySend(pathsSaverRepository.saveRatingPathList(selectedPaths))
                } catch (exception: IOException) {
                    //TODO show toast error
                    Log.w(TAG, exception)
                }
            } else {
                //TODO show toast error
            }

            newPathInfoListItemStateFlow.tryEmit(
                NewPathInfoItemState(
                    pathsMenuType,
                    PathInfoItemState(
                        PathsToAction.Multiple(selectedPathIdsInMenuList),
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
                    ConfirmDialogInfo(
                        ConfirmDialogType.DELETE_MULTIPLE_PATHS,
                        selectedPathIdsInMenuList
                    )
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