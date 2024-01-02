package ru.lobotino.walktraveller.ui

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Paint
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.ArrayMap
import android.view.LayoutInflater
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_UP
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.LocationServices
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.database.provideDatabase
import ru.lobotino.walktraveller.di.MapViewModelFactory
import ru.lobotino.walktraveller.di.PathsMenuViewModelFactory
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.SegmentRating.BADLY
import ru.lobotino.walktraveller.model.SegmentRating.GOOD
import ru.lobotino.walktraveller.model.SegmentRating.NONE
import ru.lobotino.walktraveller.model.SegmentRating.NORMAL
import ru.lobotino.walktraveller.model.SegmentRating.PERFECT
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.CachePathsRepository
import ru.lobotino.walktraveller.repositories.DatabasePathRepository
import ru.lobotino.walktraveller.repositories.FilePathsSaverRepository
import ru.lobotino.walktraveller.repositories.LastCreatedPathIdRepository
import ru.lobotino.walktraveller.repositories.LastSeenPointRepository
import ru.lobotino.walktraveller.repositories.LocationUpdatesRepository
import ru.lobotino.walktraveller.repositories.LocationsDistanceRepository
import ru.lobotino.walktraveller.repositories.OptimizePathsSettingsRepository
import ru.lobotino.walktraveller.repositories.PathDistancesInMetersRepository
import ru.lobotino.walktraveller.repositories.PathRatingRepository
import ru.lobotino.walktraveller.repositories.PathsLoaderRepository
import ru.lobotino.walktraveller.repositories.UserRotationRepository
import ru.lobotino.walktraveller.repositories.WritingPathStatesRepository
import ru.lobotino.walktraveller.repositories.permissions.AccessibilityPermissionRepository
import ru.lobotino.walktraveller.repositories.permissions.ExternalStoragePermissionsRepository
import ru.lobotino.walktraveller.repositories.permissions.GeoPermissionsRepository
import ru.lobotino.walktraveller.repositories.permissions.NotificationsPermissionsRepository
import ru.lobotino.walktraveller.services.LocationUpdatesService
import ru.lobotino.walktraveller.services.LocationUpdatesService.Companion.ACTION_START_LOCATION_UPDATES
import ru.lobotino.walktraveller.services.LocationUpdatesService.Companion.EXTRA_LOCATION
import ru.lobotino.walktraveller.services.VolumeKeysDetectorService
import ru.lobotino.walktraveller.ui.model.BottomMenuState
import ru.lobotino.walktraveller.ui.model.ConfirmDialogInfo
import ru.lobotino.walktraveller.ui.model.ConfirmDialogType.DELETE_MULTIPLE_PATHS
import ru.lobotino.walktraveller.ui.model.ConfirmDialogType.DELETE_PATH
import ru.lobotino.walktraveller.ui.model.ConfirmDialogType.GEO_LOCATION_PERMISSION_REQUIRED
import ru.lobotino.walktraveller.ui.model.MapEvent
import ru.lobotino.walktraveller.ui.model.MapUiState
import ru.lobotino.walktraveller.ui.model.PathsMenuButton
import ru.lobotino.walktraveller.ui.model.PathsMenuType
import ru.lobotino.walktraveller.ui.model.PathsToAction
import ru.lobotino.walktraveller.ui.view.FindMyLocationButton
import ru.lobotino.walktraveller.ui.view.MyPathsMenuView
import ru.lobotino.walktraveller.ui.view.OuterPathsMenuView
import ru.lobotino.walktraveller.usecases.LocalMapPathsInteractor
import ru.lobotino.walktraveller.usecases.LocalPathRedactor
import ru.lobotino.walktraveller.usecases.MapStateInteractor
import ru.lobotino.walktraveller.usecases.OuterPathsInteractor
import ru.lobotino.walktraveller.usecases.UserLocationInteractor
import ru.lobotino.walktraveller.usecases.permissions.ExternalStoragePermissionsUseCase
import ru.lobotino.walktraveller.usecases.permissions.GeoPermissionsUseCase
import ru.lobotino.walktraveller.usecases.permissions.NotificationsPermissionsUseCase
import ru.lobotino.walktraveller.usecases.permissions.VolumeKeysListenerPermissionsUseCase
import ru.lobotino.walktraveller.utils.ext.openNavigationMenu
import ru.lobotino.walktraveller.utils.ext.toGeoPoint
import ru.lobotino.walktraveller.utils.ext.toMapPoint
import ru.lobotino.walktraveller.viewmodels.MapViewModel
import ru.lobotino.walktraveller.viewmodels.PathsMenuViewModel
import kotlin.properties.Delegates

class MainMapFragment : Fragment() {

    companion object {
        private const val DEFAULT_COMFORT_ZOOM = 15.0
        private const val EXTRA_DATA_URI = "EXTRA_DATA_URI"

        fun newInstance(extraData: Uri? = null): MainMapFragment {
            return MainMapFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_DATA_URI, extraData)
                }
            }
        }
    }

    private lateinit var mapView: MapView
    private lateinit var mapViewModel: MapViewModel
    private lateinit var menuViewModel: PathsMenuViewModel
    private lateinit var walkStartButton: CardView
    private lateinit var walkStopButton: CardView
    private lateinit var openNavigationButton: CardView
    private lateinit var ratingButtonsHolder: View
    private lateinit var ratingNoneButtonHolder: View
    private lateinit var ratingBadlyButton: CardView
    private lateinit var ratingNormalButton: CardView
    private lateinit var ratingGoodButton: CardView
    private lateinit var ratingPerfectButton: CardView
    private lateinit var ratingNoneButton: CardView
    private lateinit var ratingBadlyButtonStar: ImageView
    private lateinit var ratingNormalButtonStar: ImageView
    private lateinit var ratingGoodButtonStar: ImageView
    private lateinit var ratingPerfectButtonStar: ImageView
    private lateinit var ratingNoneButtonStar: ImageView
    private lateinit var walkStopAcceptProgress: CircularProgressIndicator
    private lateinit var showPathsMenuButton: CardView

    private lateinit var myPathsMenu: MyPathsMenuView
    private lateinit var outerPathsMenu: OuterPathsMenuView
    private lateinit var walkButtonsHolder: ViewGroup

    private lateinit var findMyLocationButton: FindMyLocationButton

    private lateinit var userLocationOverlay: UserLocationOverlay

    private var ratingWhiteColor by Delegates.notNull<@ColorInt Int>()
    private var ratingPerfectColor by Delegates.notNull<@ColorInt Int>()
    private var ratingGoodColor by Delegates.notNull<@ColorInt Int>()
    private var ratingNormalColor by Delegates.notNull<@ColorInt Int>()
    private var ratingBadlyColor by Delegates.notNull<@ColorInt Int>()
    private var ratingNoneColor by Delegates.notNull<@ColorInt Int>()
    private var commonPathColor by Delegates.notNull<@ColorInt Int>()

    private val showingPathsPolylines = ArrayMap<Long, List<Polyline>>()
    private val currentPathPolylines = ArrayList<Polyline>()
    private var currentPathPolyline: Polyline? = null
    private var lastCurrentPathRating: SegmentRating? = null

    private var locationUpdatesService: LocationUpdatesService? = null

    private var stopAcceptProgressJob: Job? = null

    private lateinit var sharedPreferences: SharedPreferences

    private val serviceConnectionListener: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            locationUpdatesService =
                (service as LocationUpdatesService.LocationUpdatesBinder).locationUpdatesService
        }

        override fun onServiceDisconnected(name: ComponentName) {
            locationUpdatesService = null
        }
    }

    private val locationChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_LOCATION, Location::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_LOCATION)
            }

            if (location != null) {
                mapViewModel.onNewLocationReceive(location)
            }
        }
    }

    private val ratingChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mapViewModel.onNewRatingReceive()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false).also { view ->
            initColors()
            initViews(view)
            initViewModel(savedInstanceState)
        }
    }

    private fun initColors() {
        context?.let { context ->
            ratingWhiteColor = ContextCompat.getColor(context, R.color.white)
            ratingNoneColor = ContextCompat.getColor(context, R.color.rating_none_color)
            ratingPerfectColor = ContextCompat.getColor(context, R.color.rating_perfect_color)
            ratingGoodColor = ContextCompat.getColor(context, R.color.rating_good_color)
            ratingNormalColor = ContextCompat.getColor(context, R.color.rating_normal_color)
            ratingBadlyColor = ContextCompat.getColor(context, R.color.rating_badly_color)
            commonPathColor = ContextCompat.getColor(context, R.color.common_path_color)
        }
    }

    private fun initViews(rootLayout: View) {
        rootLayout.let { view ->
            val mapViewContainer = view.findViewById<FrameLayout>(R.id.map_view_container)

            mapView = MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                controller.setZoom(DEFAULT_COMFORT_ZOOM)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setMultiTouchControls(true)
                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent): Boolean {
                        mapViewModel.onMapScrolled(event.source.mapCenter.toMapPoint())
                        return true
                    }

                    override fun onZoom(event: ZoomEvent): Boolean {
                        mapViewModel.onMapZoomed()
                        return true
                    }
                })
            }
            mapViewContainer.addView(mapView)

            userLocationOverlay = UserLocationOverlay(requireContext())

            ratingButtonsHolder = view.findViewById<CardView>(R.id.rating_buttons_holder)
            ratingNoneButtonHolder = view.findViewById<CardView>(R.id.rating_none_button_holder)

            walkStartButton = view.findViewById<CardView>(R.id.walk_start_button)
                .apply {
                    setOnClickListener { mapViewModel.onStartPathButtonClicked() }
                }

            ratingPerfectButton = view.findViewById<CardView>(R.id.rating_perfect)
                .apply {
                    setOnClickListener { mapViewModel.onRatingButtonClicked(PERFECT) }
                }
            ratingPerfectButtonStar = view.findViewById(R.id.rating_perfect_star)

            ratingGoodButton = view.findViewById<CardView>(R.id.rating_good)
                .apply {
                    setOnClickListener { mapViewModel.onRatingButtonClicked(GOOD) }
                }
            ratingGoodButtonStar = view.findViewById(R.id.rating_good_star)

            ratingNormalButton = view.findViewById<CardView>(R.id.rating_normal)
                .apply {
                    setOnClickListener { mapViewModel.onRatingButtonClicked(NORMAL) }
                }
            ratingNormalButtonStar = view.findViewById(R.id.rating_normal_star)

            ratingBadlyButton = view.findViewById<CardView>(R.id.rating_badly)
                .apply {
                    setOnClickListener { mapViewModel.onRatingButtonClicked(BADLY) }
                }
            ratingBadlyButtonStar = view.findViewById(R.id.rating_badly_star)

            ratingNoneButton = view.findViewById<CardView>(R.id.rating_none)
                .apply {
                    setOnClickListener { mapViewModel.onRatingButtonClicked(NONE) }
                }
            ratingNoneButtonStar = view.findViewById(R.id.rating_none_star)

            walkStopAcceptProgress = view.findViewById(R.id.walk_stop_accept_progress)

            walkStopButton = view.findViewById<CardView>(R.id.walk_stop_button)
                .apply {
                    setOnTouchListener { _, event ->
                        when (event.action) {
                            ACTION_DOWN -> {
                                isPressed = true
                                walkStopAcceptProgressStart {
                                    isPressed = false
                                    performClick()
                                }
                                true
                            }

                            ACTION_UP -> {
                                tryCancelWalkStopAcceptProgress().also { canceled ->
                                    if (canceled) {
                                        isPressed = false
                                    }
                                }
                                true
                            }

                            else -> {
                                true
                            }
                        }
                    }
                }

            walkStopButton.setOnClickListener { mapViewModel.onStopPathButtonClicked() }

            myPathsMenu = view.findViewById<MyPathsMenuView>(R.id.my_paths_menu).apply {
                setupOnClickListeners(
                    menuTitleButtonClickListener = { pathsMenuButton ->
                        when (pathsMenuButton) {
                            is PathsMenuButton.SelectAll -> menuViewModel.onSelectAllPathsButtonClicked(
                                PathsMenuType.MY_PATHS,
                                pathsMenuButton.pathsIdsInList
                            )

                            PathsMenuButton.FilterPathsColor -> menuViewModel.onShowPathsFilterButtonClicked()
                            PathsMenuButton.Back -> menuViewModel.onPathsMenuBackButtonClicked()
                            PathsMenuButton.ShowSelectedPaths -> menuViewModel.onShowSelectedPathsButtonClicked(PathsMenuType.MY_PATHS)
                            PathsMenuButton.ShareSelectedPaths -> menuViewModel.onShareSelectedPathsButtonClicked(PathsMenuType.MY_PATHS)
                            PathsMenuButton.DeleteSelectedPaths -> menuViewModel.onDeleteSelectedPathsButtonClicked(PathsMenuType.MY_PATHS)
                        }
                    },
                    itemButtonClickedListener = { pathId, itemButtonClickedType ->
                        menuViewModel.onPathInListButtonClicked(pathId, itemButtonClickedType, PathsMenuType.MY_PATHS)
                    },
                    itemShortTapListener = { pathId ->
                        menuViewModel.onPathInListShortTap(pathId, PathsMenuType.MY_PATHS)
                    },
                    itemLongTapListener = { pathId ->
                        menuViewModel.onPathInListLongTap(pathId, PathsMenuType.MY_PATHS)
                    }
                )
            }

            outerPathsMenu = view.findViewById<OuterPathsMenuView>(R.id.outer_paths_menu).apply {
                setupOnClickListeners(
                    menuTitleButtonClickListener = { pathsMenuButton ->
                        when (pathsMenuButton) {
                            PathsMenuButton.ShowSelectedPaths -> menuViewModel.onShowSelectedPathsButtonClicked(PathsMenuType.OUTER_PATHS)
                            PathsMenuButton.DeleteSelectedPaths -> menuViewModel.onDeleteSelectedPathsButtonClicked(PathsMenuType.OUTER_PATHS)
                            PathsMenuButton.Back -> menuViewModel.onPathsMenuBackButtonClicked()
                            else -> {}
                        }
                    },
                    confirmButtonClickListener = {
                        menuViewModel.onOuterPathsConfirmButtonClicked()
                    },
                    itemButtonClickedListener = { pathId, itemButtonClickedType ->
                        menuViewModel.onPathInListButtonClicked(pathId, itemButtonClickedType, PathsMenuType.OUTER_PATHS)
                    },
                    itemShortTapListener = { pathId ->
                        menuViewModel.onPathInListShortTap(pathId, PathsMenuType.OUTER_PATHS)
                    },
                    itemLongTapListener = { pathId ->
                        menuViewModel.onPathInListLongTap(pathId, PathsMenuType.OUTER_PATHS)
                    }
                )
            }

            walkButtonsHolder = view.findViewById(R.id.walk_buttons_holder)

            showPathsMenuButton = view.findViewById<CardView>(R.id.show_paths_menu_button).apply {
                setOnClickListener {
                    menuViewModel.onShowPathsMenuButtonClick()
                }
            }

            openNavigationButton =
                view.findViewById<CardView>(R.id.show_navigation_menu_button).apply {
                    setOnClickListener {
                        openNavigationMenu()
                    }
                }

            findMyLocationButton =
                view.findViewById<FindMyLocationButton>(R.id.my_location_button).apply {
                    setOnClickListener { mapViewModel.onFindMyLocationButtonClicked() }
                }
        }
    }

    private fun initViewModel(bundle: Bundle?) {
        if (activity != null && context != null) {
            sharedPreferences = requireContext().getSharedPreferences(
                App.SHARED_PREFS_TAG,
                AppCompatActivity.MODE_PRIVATE
            )

            val writingPathStatesRepository =
                WritingPathStatesRepository(sharedPreferences)

            val lastCreatedPathIdRepository =
                LastCreatedPathIdRepository(sharedPreferences)

            val databasePathRepository = DatabasePathRepository(
                provideDatabase(requireContext().applicationContext),
                lastCreatedPathIdRepository
            )

            val pathDistancesInMetersRepository = PathDistancesInMetersRepository(LocationsDistanceRepository())

            val pathRedactor = LocalPathRedactor(
                databasePathRepository,
                pathDistancesInMetersRepository
            )

            val mapPathsInteractor = LocalMapPathsInteractor(
                databasePathRepository = databasePathRepository,
                cachePathRepository = CachePathsRepository(),
                writingPathStatesRepository = writingPathStatesRepository,
                lastCreatedPathIdRepository = lastCreatedPathIdRepository,
                pathRedactor = pathRedactor,
                optimizePathsSettingsRepository = OptimizePathsSettingsRepository(
                    sharedPreferences
                )
            )

            menuViewModel = ViewModelProvider(
                this,
                PathsMenuViewModelFactory(
                    externalStoragePermissionsUseCase = ExternalStoragePermissionsUseCase(
                        ExternalStoragePermissionsRepository(
                            this@MainMapFragment,
                            requireContext().applicationContext
                        )
                    ),
                    mapPathsInteractor = mapPathsInteractor,
                    pathsSaverRepository = FilePathsSaverRepository(requireContext().applicationContext),
                    outerPathsInteractor = OuterPathsInteractor(
                        PathsLoaderRepository(requireContext().applicationContext),
                        pathDistancesInMetersRepository,
                        databasePathRepository
                    ),
                    pathRedactor = pathRedactor,
                    owner = this,
                    bundle = bundle
                )
            )[PathsMenuViewModel::class.java].apply {
                observeMyPathsMenuUiState.onEach { myPathsUiState ->
                    myPathsMenu.syncState(myPathsUiState)
                }.launchIn(lifecycleScope)

                observeOuterPathsMenuUiState.onEach { outerPathsUiState ->
                    outerPathsMenu.syncState(outerPathsUiState)
                }.launchIn(lifecycleScope)

                observeNewMapEvent.onEach { mapEvent ->
                    when (mapEvent) {
                        is MapEvent.ClearMap -> {
                            mapViewModel.clearMap()
                        }

                        is MapEvent.ShowRatingPath -> {
                            mapViewModel.showRatingPathOnMap(mapEvent.path)
                        }

                        is MapEvent.ShowCommonPath -> {
                            mapViewModel.showCommonPathOnMap(mapEvent.path)
                        }

                        is MapEvent.ShowRatingPathList -> {
                            mapViewModel.showRatingPathListOnMap(mapEvent.pathList)
                        }

                        is MapEvent.ShowCommonPathList -> {
                            mapViewModel.showCommonPathListOnMap(mapEvent.pathList)
                        }

                        is MapEvent.HidePath -> {
                            mapViewModel.hidePathsFromMap(mapEvent.pathsToHide)
                        }

                        is MapEvent.BottomMenuStateChange -> {
                            mapViewModel.onBottomMenuStateChange(mapEvent.newBottomMenuState)
                        }
                    }
                }.launchIn(lifecycleScope)

                observeNewPathsInfoList.onEach { newPathsInfoListEvent ->
                    when (newPathsInfoListEvent.pathsMenuType) {
                        PathsMenuType.MY_PATHS -> myPathsMenu.setPathsInfoItems(newPathsInfoListEvent.newPathInfoList)
                        PathsMenuType.OUTER_PATHS -> outerPathsMenu.setPathsInfoItems(newPathsInfoListEvent.newPathInfoList)
                    }
                }.launchIn(lifecycleScope)

                observeNewPathInfoListItemState.onEach { newPathInfoStateEvent ->
                    when (newPathInfoStateEvent.pathsMenuType) {
                        PathsMenuType.MY_PATHS -> myPathsMenu.syncPathInfoItemState(newPathInfoStateEvent.pathInfoItemState)
                        PathsMenuType.OUTER_PATHS -> outerPathsMenu.syncPathInfoItemState(newPathInfoStateEvent.pathInfoItemState)
                    }
                }.launchIn(lifecycleScope)

                observeShareFileChannel.onEach { sharedFileUri ->
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "application/*"
                        putExtra(Intent.EXTRA_STREAM, sharedFileUri)
                    }
                    startActivity(Intent.createChooser(sendIntent, getString(R.string.share_file_title)))
                }.launchIn(lifecycleScope)

                observeDeletePathInfoItemChannel.onEach { deletePathInfoItemEvent ->
                    when (deletePathInfoItemEvent.pathsMenuType) {
                        PathsMenuType.MY_PATHS -> myPathsMenu.deletePathInfoItem(deletePathInfoItemEvent.pathsToDelete)
                        PathsMenuType.OUTER_PATHS -> outerPathsMenu.deletePathInfoItem(deletePathInfoItemEvent.pathsToDelete)
                    }
                }.launchIn(lifecycleScope)

                observeNewConfirmDialog.onEach { confirmDialogInfo ->
                    showConfirmDialog(confirmDialogInfo)
                }.launchIn(lifecycleScope)
            }

            mapViewModel =
                ViewModelProvider(
                    this,
                    MapViewModelFactory(
                        notificationsPermissionsInteractor = NotificationsPermissionsUseCase(
                            NotificationsPermissionsRepository(
                                this@MainMapFragment,
                                requireContext().applicationContext
                            )
                        ),
                        volumeKeysListenerPermissionsInteractor = VolumeKeysListenerPermissionsUseCase(
                            AccessibilityPermissionRepository(
                                requireContext().applicationContext
                            )
                        ),
                        geoPermissionsUseCase = GeoPermissionsUseCase(
                            GeoPermissionsRepository(
                                this@MainMapFragment,
                                requireContext().applicationContext
                            )
                        ),
                        userLocationInteractor = UserLocationInteractor(
                            LocationUpdatesRepository(
                                LocationServices.getFusedLocationProviderClient(requireActivity()),
                                5000
                            )
                        ),
                        mapPathsInteractor = mapPathsInteractor,
                        mapStateInteractor = MapStateInteractor(
                            LastSeenPointRepository(
                                sharedPreferences
                            )
                        ),
                        writingPathStatesRepository = writingPathStatesRepository,
                        pathRatingRepository = PathRatingRepository(sharedPreferences),
                        userRotationRepository = UserRotationRepository(
                            requireActivity().getSystemService(
                                SENSOR_SERVICE
                            ) as SensorManager,
                            lifecycleScope
                        ),
                        owner = this,
                        bundle = bundle
                    )
                )[MapViewModel::class.java].apply {
                    observePermissionsDeniedResult.onEach {
                        showPermissionsDeniedError()
                    }.launchIn(lifecycleScope)

                    observeNewPathSegment.onEach { pathSegment ->
                        paintNewCurrentPathSegment(pathSegment)
                    }.launchIn(lifecycleScope)

                    observeNewCommonPath.onEach { pathList ->
                        paintNewCommonPaths(pathList, commonPathColor)
                    }.launchIn(lifecycleScope)

                    observeNewRatingPath.onEach { pathList ->
                        paintNewRatingPaths(pathList)
                    }.launchIn(lifecycleScope)

                    observeMapUiState.onEach { mapUiState ->
                        updateMapUiState(mapUiState)
                    }.launchIn(lifecycleScope)

                    observeRegularLocationUpdate.onEach { needToUpdateLocation ->
                        if (needToUpdateLocation) {
                            sendStartLocationUpdatesAction()
                        } else {
                            sendStopLocationUpdatesAction()
                        }
                    }.launchIn(lifecycleScope)

                    observeNewMapCenter.onEach { newMapCenter ->
                        mapView.controller?.let { mapViewController ->
                            mapViewController.setCenter(newMapCenter.toGeoPoint())
                            if (mapView.zoomLevelDouble < DEFAULT_COMFORT_ZOOM) {
                                mapViewController.setZoom(DEFAULT_COMFORT_ZOOM)
                            }
                        }
                    }.launchIn(lifecycleScope)

                    observeHidePath.onEach { pathsToHide ->
                        when (pathsToHide) {
                            PathsToAction.All -> {
                                this@MainMapFragment.clearMap()
                            }

                            is PathsToAction.Single -> {
                                hidePathById(pathsToHide.pathId)
                            }

                            is PathsToAction.Multiple -> {
                                hidePathsList(pathsToHide.pathIds)
                            }
                        }
                    }.launchIn(lifecycleScope)

                    observeNewCurrentUserLocation.onEach { newUserLocation ->
                        userLocationOverlay.setPosition(newUserLocation.toGeoPoint())
                        refreshMapNow()
                    }.launchIn(lifecycleScope)

                    observeWritingPathNow.onEach { isWritingPathNow ->
                        if (isWritingPathNow) {
                            sendStartCurrentPathAction()
                            walkStartButton.visibility = GONE
                            walkStopButton.visibility = VISIBLE
                            ratingButtonsHolder.visibility = VISIBLE
                            ratingNoneButtonHolder.visibility = VISIBLE
                        } else {
                            sendFinishCurrentPathAction()
                            walkStartButton.visibility = VISIBLE
                            walkStopButton.visibility = GONE
                            ratingButtonsHolder.visibility = GONE
                            ratingNoneButtonHolder.visibility = GONE
                        }
                    }.launchIn(lifecycleScope)

                    observeNewConfirmDialog.onEach { confirmDialogInfo ->
                        showConfirmDialog(confirmDialogInfo)
                    }.launchIn(lifecycleScope)

                    observeNewUserRotation().onEach { newUserRotation ->
                        userLocationOverlay.setRotation(newUserRotation)
                        refreshMapNow()
                    }.launchIn(lifecycleScope)

                    onInitFinish()
                }
        }
    }

    private fun showConfirmDialog(confirmDialogInfo: ConfirmDialogInfo) {
        context?.let { context ->
            when (confirmDialogInfo.dialogType) {
                DELETE_PATH -> {
                    DeleteConfirmDialog(
                        context = context,
                        onConfirm = { menuViewModel.onConfirmMyPathDelete(confirmDialogInfo.additionalInfo as Long) }
                    ).show()
                }

                GEO_LOCATION_PERMISSION_REQUIRED -> {
                    GeoLocationRequiredDialog(
                        context = context,
                        onConfirm = { mapViewModel.onLocationPermissionDialogConfirmed() }
                    ).show()
                }

                DELETE_MULTIPLE_PATHS -> {
                    DeleteConfirmDialog(
                        context = context,
                        onConfirm = {
                            @Suppress("UNCHECKED_CAST")
                            menuViewModel.onConfirmMyPathListDelete(confirmDialogInfo.additionalInfo as List<Long>)
                        }
                    ).show()
                }
            }
        }
    }

    private fun sendStartLocationUpdatesAction() {
        locationUpdatesService?.startLocationUpdates() ?: activity?.let { activity ->
            activity.startService(
                Intent(activity, LocationUpdatesService::class.java).apply {
                    action = ACTION_START_LOCATION_UPDATES
                }
            )
        }
    }

    private fun sendStopLocationUpdatesAction() {
        locationUpdatesService?.stopLocationUpdates()
    }

    private fun sendStartCurrentPathAction() {
        locationUpdatesService?.startWritingPath()
    }

    private fun sendFinishCurrentPathAction() {
        locationUpdatesService?.finishWritingPath()
    }

    override fun onStart() {
        activity?.bindService(
            Intent(activity, LocationUpdatesService::class.java),
            serviceConnectionListener,
            Context.BIND_AUTO_CREATE
        )
        super.onStart()
    }

    override fun onStop() {
        activity?.unbindService(serviceConnectionListener)
        super.onStop()
    }

    override fun onResume() {
        mapView.onResume()
        mapViewModel.onResume()
        menuViewModel.onResume(getExtraData())
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            locationChangeReceiver,
            IntentFilter(LocationUpdatesService.ACTION_BROADCAST)
        )
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            ratingChangeReceiver,
            IntentFilter(VolumeKeysDetectorService.RATING_CHANGES_BROADCAST)
        )
        super.onResume()
    }

    private fun getExtraData(): Uri? {
        val extraData: Uri? = arguments?.getParcelable(EXTRA_DATA_URI)
        if (extraData != null) {
            arguments?.remove(EXTRA_DATA_URI)
        }
        return extraData
    }

    override fun onPause() {
        mapView.onPause()
        mapViewModel.onPause()
        LocalBroadcastManager
            .getInstance(requireContext())
            .unregisterReceiver(locationChangeReceiver)
        super.onPause()
    }

    private fun updateMapUiState(mapUiState: MapUiState) {
        if (mapUiState.isPathFinished) {
            setLastPathFinished()
        }

        when (mapUiState.bottomMenuState) {
            BottomMenuState.DEFAULT -> {
                myPathsMenu.visibility = GONE
                outerPathsMenu.visibility = GONE
                walkButtonsHolder.visibility = VISIBLE
            }

            BottomMenuState.MY_PATHS_MENU -> {
                myPathsMenu.visibility = VISIBLE
                outerPathsMenu.visibility = GONE
                walkButtonsHolder.visibility = GONE
            }

            BottomMenuState.OUTER_PATHS_MENU -> {
                myPathsMenu.visibility = GONE
                outerPathsMenu.visibility = VISIBLE
                walkButtonsHolder.visibility = GONE
            }
        }

        findMyLocationButton.updateState(mapUiState.findMyLocationButtonState)

        syncRatingButtons(mapUiState.newRating)

        refreshMapNow()
    }

    private fun syncRatingButtons(currentRating: SegmentRating) {
        selectRatingButton(
            ratingPerfectButton,
            ratingPerfectButtonStar,
            ratingPerfectColor,
            ratingWhiteColor,
            currentRating == PERFECT
        )
        selectRatingButton(
            ratingGoodButton,
            ratingGoodButtonStar,
            ratingGoodColor,
            ratingWhiteColor,
            currentRating == GOOD
        )
        selectRatingButton(
            ratingNormalButton,
            ratingNormalButtonStar,
            ratingNormalColor,
            ratingWhiteColor,
            currentRating == NORMAL
        )
        selectRatingButton(
            ratingBadlyButton,
            ratingBadlyButtonStar,
            ratingBadlyColor,
            ratingWhiteColor,
            currentRating == BADLY
        )
        selectRatingButton(
            ratingNoneButton,
            ratingNoneButtonStar,
            ratingNoneColor,
            ratingWhiteColor,
            currentRating == NONE
        )
    }

    private fun selectRatingButton(
        ratingButton: CardView,
        ratingButtonStar: ImageView,
        @ColorInt selectedColor: Int,
        @ColorInt unselectedColor: Int,
        selected: Boolean
    ) {
        ratingButton.setCardBackgroundColor(if (selected) selectedColor else unselectedColor)
        ImageViewCompat.setImageTintList(
            ratingButtonStar,
            ColorStateList.valueOf(if (selected) unselectedColor else selectedColor)
        )
    }

    private fun clearMap() {
        mapView.overlays.clear()
        showingPathsPolylines.clear()
        addUserLocationTracker()
    }

    private fun showPermissionsDeniedError() {
        if (context != null) {
            Toast.makeText(
                context,
                getString(R.string.permissions_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun paintNewCommonPaths(pathList: List<MapCommonPath>, color: Int) {
        context ?: return

        val allAddedPolylines = ArrayList<Polyline>()
        for (path in pathList) {
            val pathPolyline = Polyline(mapView).apply {
                outlinePaint.color = color

                setPoints(
                    path.pathPoints.map { point ->
                        GeoPoint(
                            point.latitude,
                            point.longitude
                        )
                    }
                )
            }
            showingPathsPolylines[path.pathId] = listOf(pathPolyline)
            allAddedPolylines.add(pathPolyline)
        }
        mapView.overlays.addAll(allAddedPolylines)
        refreshMapNow()
    }

    private fun paintNewRatingPaths(pathList: List<MapRatingPath>) {
        context ?: return

        val allAddedPolylines = ArrayList<Polyline>()
        var lastAddedPolyline: Polyline? = null
        var lastSegment: MapPathSegment? = null
        for (path in pathList) {
            val pathPolylines = ArrayList<Polyline>()
            for (segment in path.pathSegments) {
                if (lastAddedPolyline == null) {
                    lastAddedPolyline = createRatingSegmentPolyline(segment)
                    lastSegment = segment
                    pathPolylines.add(lastAddedPolyline)
                } else {
                    if (lastSegment != null && segment.rating == lastSegment.rating && segment.startPoint == lastSegment.finishPoint) {
                        lastAddedPolyline.addPoint(segment.finishPoint.toGeoPoint())
                    } else {
                        lastAddedPolyline = createRatingSegmentPolyline(segment)
                        lastSegment = segment
                        pathPolylines.add(lastAddedPolyline)
                    }
                }
            }
            showingPathsPolylines[path.pathId] = pathPolylines
            allAddedPolylines.addAll(pathPolylines)
        }
        mapView.overlays.addAll(allAddedPolylines)
        refreshMapNow()
    }

    private fun createRatingSegmentPolyline(pathSegment: MapPathSegment): Polyline {
        return Polyline(mapView).apply {
            outlinePaint.apply {
                addPoint(pathSegment.startPoint.toGeoPoint())
                addPoint(pathSegment.finishPoint.toGeoPoint())
                getRatingColor(pathSegment.rating)?.let { ratingColor ->
                    color = ratingColor
                }
                strokeCap = Paint.Cap.ROUND
            }
        }
    }

    private fun paintNewCurrentPathSegment(pathSegment: MapPathSegment) {
        if (currentPathPolyline == null) {
            currentPathPolyline = createRatingSegmentPolyline(pathSegment)
            lastCurrentPathRating = pathSegment.rating
            mapView.overlays.add(currentPathPolyline)
            currentPathPolylines.add(currentPathPolyline!!)
        } else {
            if (pathSegment.rating == lastCurrentPathRating) {
                currentPathPolyline!!.addPoint(pathSegment.finishPoint.toGeoPoint())
            } else {
                currentPathPolyline = createRatingSegmentPolyline(pathSegment)
                lastCurrentPathRating = pathSegment.rating
                mapView.overlays.add(currentPathPolyline)
                currentPathPolylines.add(currentPathPolyline!!)
            }
        }

        refreshMapNow()
    }

    private fun hidePathById(pathId: Long) {
        val hidingPath = showingPathsPolylines[pathId] ?: return

        showingPathsPolylines.remove(pathId)
        mapView.overlays.removeAll(hidingPath)
        refreshMapNow()
    }

    private fun hidePathsList(pathIds: List<Long>) {
        val segmentsToHide: MutableList<Overlay> = ArrayList()
        for (pathId in pathIds) {
            val pathsSegments = showingPathsPolylines[pathId] ?: continue
            showingPathsPolylines.remove(pathId)
            segmentsToHide.addAll(pathsSegments)
        }
        if (segmentsToHide.isNotEmpty()) {
            mapView.overlays.removeAll(segmentsToHide)
            refreshMapNow()
        }
    }

    @ColorInt
    private fun getRatingColor(segmentRating: SegmentRating): Int? {
        context?.let { context ->
            when (segmentRating) {
                PERFECT -> return@getRatingColor ContextCompat.getColor(
                    context,
                    R.color.rating_perfect
                )

                GOOD -> return@getRatingColor ContextCompat.getColor(context, R.color.rating_good)
                NORMAL -> return@getRatingColor ContextCompat.getColor(
                    context,
                    R.color.rating_normal
                )

                BADLY -> return@getRatingColor ContextCompat.getColor(context, R.color.rating_badly)
                NONE -> return@getRatingColor ContextCompat.getColor(context, R.color.rating_none)
            }
        }
        return null
    }

    private fun setLastPathFinished() {
        currentPathPolylines.clear()
        currentPathPolyline = null
        lastCurrentPathRating = null
    }

    private fun refreshMapNow() {
        mapView.postInvalidate()
    }

    private fun walkStopAcceptProgressStart(onFinished: () -> Unit) {
        stopAcceptProgressJob = CoroutineScope(Dispatchers.Default).launch {
            while (walkStopAcceptProgress.progress != 100) {
                delay(5)
                withContext(Dispatchers.Main) {
                    walkStopAcceptProgress.progress += 1
                }
            }
            withContext(Dispatchers.Main) {
                onFinished()
            }
        }
    }

    /**
     * @return success of canceling
     */
    private fun tryCancelWalkStopAcceptProgress(): Boolean {
        return if (stopAcceptProgressJob != null) {
            stopAcceptProgressJob!!.cancel()
            walkStopAcceptProgress.progress = 0
            true
        } else {
            false
        }
    }

    private fun addUserLocationTracker() {
        mapView.overlays.add(userLocationOverlay)
    }
}
