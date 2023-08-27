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
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
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
import org.osmdroid.views.overlay.Polyline
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.database.provideDatabase
import ru.lobotino.walktraveller.di.MapViewModelFactory
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.SegmentRating.BADLY
import ru.lobotino.walktraveller.model.SegmentRating.GOOD
import ru.lobotino.walktraveller.model.SegmentRating.NONE
import ru.lobotino.walktraveller.model.SegmentRating.NORMAL
import ru.lobotino.walktraveller.model.SegmentRating.PERFECT
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.AccessibilityPermissionRepository
import ru.lobotino.walktraveller.repositories.CachePathsRepository
import ru.lobotino.walktraveller.repositories.DatabasePathRepository
import ru.lobotino.walktraveller.repositories.GeoPermissionsRepository
import ru.lobotino.walktraveller.repositories.LastCreatedPathIdRepository
import ru.lobotino.walktraveller.repositories.LastSeenPointRepository
import ru.lobotino.walktraveller.repositories.LocationUpdatesRepository
import ru.lobotino.walktraveller.repositories.LocationsDistanceRepository
import ru.lobotino.walktraveller.repositories.NotificationsPermissionsRepository
import ru.lobotino.walktraveller.repositories.PathColorGenerator
import ru.lobotino.walktraveller.repositories.PathDistancesInMetersRepository
import ru.lobotino.walktraveller.repositories.PathRatingRepository
import ru.lobotino.walktraveller.repositories.UserRotationRepository
import ru.lobotino.walktraveller.repositories.WritingPathStatesRepository
import ru.lobotino.walktraveller.services.LocationUpdatesService
import ru.lobotino.walktraveller.services.LocationUpdatesService.Companion.ACTION_START_LOCATION_UPDATES
import ru.lobotino.walktraveller.services.LocationUpdatesService.Companion.EXTRA_LOCATION
import ru.lobotino.walktraveller.services.VolumeKeysDetectorService
import ru.lobotino.walktraveller.ui.model.BottomMenuState
import ru.lobotino.walktraveller.ui.model.ConfirmDialogInfo
import ru.lobotino.walktraveller.ui.model.ConfirmDialogType
import ru.lobotino.walktraveller.ui.model.MapUiState
import ru.lobotino.walktraveller.ui.model.PathInfoItemState
import ru.lobotino.walktraveller.ui.model.PathsInfoListState
import ru.lobotino.walktraveller.ui.model.ShowPathsButtonState
import ru.lobotino.walktraveller.ui.model.ShowPathsFilterButtonState
import ru.lobotino.walktraveller.usecases.DistanceInMetersToStringFormatter
import ru.lobotino.walktraveller.usecases.GeoPermissionsInteractor
import ru.lobotino.walktraveller.usecases.LocalMapPathsInteractor
import ru.lobotino.walktraveller.usecases.LocalPathRedactor
import ru.lobotino.walktraveller.usecases.MapStateInteractor
import ru.lobotino.walktraveller.usecases.NotificationsPermissionsInteractor
import ru.lobotino.walktraveller.usecases.UserLocationInteractor
import ru.lobotino.walktraveller.usecases.VolumeKeysListenerPermissionsInteractor
import ru.lobotino.walktraveller.utils.ext.toColorInt
import ru.lobotino.walktraveller.utils.ext.toGeoPoint
import ru.lobotino.walktraveller.utils.ext.toMapPoint
import ru.lobotino.walktraveller.viewmodels.MapViewModel
import kotlin.properties.Delegates


class MainMapFragment : Fragment() {

    companion object {
        private const val DEFAULT_COMFORT_ZOOM = 15.0
    }

    private lateinit var mapView: MapView
    private lateinit var viewModel: MapViewModel
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
    private lateinit var showPathsFilterButton: CardView
    private lateinit var showPathsFilterRatedOnlyStateImage: View
    private lateinit var showPathsFilterAllInCommonStateImage: View
    private lateinit var showAllPathsButton: CardView
    private lateinit var showAllPathsProgress: CircularProgressIndicator
    private lateinit var showAllPathsDefaultImage: ImageView
    private lateinit var showAllPathsHideImage: ImageView
    private lateinit var showPathsMenuButton: CardView
    private lateinit var pathsMenu: ViewGroup
    private lateinit var walkButtonsHolder: ViewGroup
    private lateinit var hidePathsMenuButton: ImageView
    private lateinit var pathsInfoList: RecyclerView
    private lateinit var pathsEmptyListError: ViewGroup
    private lateinit var pathsInfoProgress: CircularProgressIndicator
    private lateinit var findMyLocationButton: FindMyLocationButton

    private lateinit var userLocationOverlay: UserLocationOverlay

    private lateinit var pathsInfoListAdapter: PathsInfoAdapter

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
                viewModel.onNewLocationReceive(location)
            }
        }
    }

    private val ratingChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.onNewRatingReceive()
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
                        viewModel.onMapScrolled(event.source.mapCenter.toMapPoint())
                        return true
                    }

                    override fun onZoom(event: ZoomEvent): Boolean {
                        viewModel.onMapZoomed()
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
                    setOnClickListener { viewModel.onStartPathButtonClicked() }
                }

            ratingPerfectButton = view.findViewById<CardView>(R.id.rating_perfect)
                .apply {
                    setOnClickListener { viewModel.onRatingButtonClicked(PERFECT) }
                }
            ratingPerfectButtonStar = view.findViewById(R.id.rating_perfect_star)

            ratingGoodButton = view.findViewById<CardView>(R.id.rating_good)
                .apply {
                    setOnClickListener { viewModel.onRatingButtonClicked(GOOD) }
                }
            ratingGoodButtonStar = view.findViewById(R.id.rating_good_star)

            ratingNormalButton = view.findViewById<CardView>(R.id.rating_normal)
                .apply {
                    setOnClickListener { viewModel.onRatingButtonClicked(NORMAL) }
                }
            ratingNormalButtonStar = view.findViewById(R.id.rating_normal_star)

            ratingBadlyButton = view.findViewById<CardView>(R.id.rating_badly)
                .apply {
                    setOnClickListener { viewModel.onRatingButtonClicked(BADLY) }
                }
            ratingBadlyButtonStar = view.findViewById(R.id.rating_badly_star)

            ratingNoneButton = view.findViewById<CardView>(R.id.rating_none)
                .apply {
                    setOnClickListener { viewModel.onRatingButtonClicked(NONE) }
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

            walkStopButton.setOnClickListener { viewModel.onStopPathButtonClicked() }

            showAllPathsButton = view.findViewById<CardView>(R.id.show_all_paths_button).apply {
                setOnClickListener { viewModel.onShowAllPathsButtonClicked() }
            }

            showAllPathsProgress = view.findViewById(R.id.show_all_paths_progress)
            showAllPathsDefaultImage = view.findViewById(R.id.show_all_paths_default_image)
            showAllPathsHideImage = view.findViewById(R.id.show_all_paths_hide_image)

            showPathsFilterButton =
                view.findViewById<CardView>(R.id.show_paths_filter_button).apply {
                    setOnClickListener { viewModel.onShowPathsFilterButtonClicked() }
                }
            showPathsFilterAllInCommonStateImage =
                view.findViewById(R.id.show_paths_filter_button_all_in_common_state)
            showPathsFilterRatedOnlyStateImage =
                view.findViewById(R.id.show_paths_filter_button_rated_only_state)

            pathsMenu = view.findViewById(R.id.paths_menu)
            walkButtonsHolder = view.findViewById(R.id.walk_buttons_holder)
            pathsInfoList = view.findViewById<RecyclerView>(R.id.paths_list).apply {
                pathsInfoListAdapter =
                    PathsInfoAdapter(
                        DistanceInMetersToStringFormatter(
                            requireContext().getString(R.string.meters_full),
                            requireContext().getString(R.string.kilometers_full),
                            requireContext().getString(R.string.kilometers_short)
                        ),
                        MostCommonRating.values().map { it.toColorInt(requireContext()) }
                    ) { pathId, itemButtonClickedType ->
                        viewModel.onPathInListButtonClicked(pathId, itemButtonClickedType)
                    }
                if (itemAnimator is SimpleItemAnimator) {
                    (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
                }
                adapter = pathsInfoListAdapter
            }
            pathsInfoProgress = view.findViewById(R.id.paths_list_progress)
            pathsEmptyListError = view.findViewById(R.id.empty_paths_error)
            showPathsMenuButton = view.findViewById<CardView>(R.id.show_paths_menu_button).apply {
                setOnClickListener { viewModel.onShowPathsMenuClicked() }
            }
            hidePathsMenuButton = view.findViewById<ImageView>(R.id.paths_menu_back_button).apply {
                setOnClickListener { viewModel.onHidePathsMenuClicked() }
            }
            openNavigationButton =
                view.findViewById<CardView>(R.id.show_navigation_menu_button).apply {
                    setOnClickListener {
                        if (activity != null) {
                            (requireActivity() as MainActivity).openNavigationMenu() //fixme
                        }
                    }
                }
            findMyLocationButton =
                view.findViewById<FindMyLocationButton>(R.id.my_location_button).apply {
                    setOnClickListener { viewModel.onFindMyLocationButtonClicked() }
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

            val pathRedactor = LocalPathRedactor(
                databasePathRepository,
                PathDistancesInMetersRepository(LocationsDistanceRepository())
            )

            viewModel =
                ViewModelProvider(
                    this, MapViewModelFactory(
                        notificationsPermissionsInteractor = NotificationsPermissionsInteractor(
                            NotificationsPermissionsRepository(
                                this@MainMapFragment,
                                requireContext().applicationContext
                            )
                        ),
                        volumeKeysListenerPermissionsInteractor = VolumeKeysListenerPermissionsInteractor(
                            AccessibilityPermissionRepository(
                                requireContext().applicationContext
                            )
                        ),
                        geoPermissionsInteractor = GeoPermissionsInteractor(
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
                        mapPathsInteractor = LocalMapPathsInteractor(
                            databasePathRepository = databasePathRepository,
                            cachePathRepository = CachePathsRepository(),
                            pathColorGenerator = PathColorGenerator(requireContext()),
                            writingPathStatesRepository = writingPathStatesRepository,
                            lastCreatedPathIdRepository = lastCreatedPathIdRepository,
                            pathRedactor = pathRedactor
                        ),
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
                        pathRedactor = pathRedactor,
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

                    observeNewCommonPath.onEach { path ->
                        paintNewCommonPath(path, commonPathColor)
                    }.launchIn(lifecycleScope)

                    observeNewRatingPath.onEach { path ->
                        paintNewRatingPath(path)
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

                    observeNewPathsInfoList.onEach { newPathsInfoList ->
                        pathsInfoListAdapter.setPathsInfoItems(newPathsInfoList)
                    }.launchIn(lifecycleScope)

                    observeNewMapCenter.onEach { newMapCenter ->
                        mapView.controller?.let { mapViewController ->
                            mapViewController.setCenter(newMapCenter.toGeoPoint())
                            if (mapView.zoomLevelDouble < DEFAULT_COMFORT_ZOOM) {
                                mapViewController.setZoom(DEFAULT_COMFORT_ZOOM)
                            }
                        }
                    }.launchIn(lifecycleScope)

                    observeNewPathInfoListItemState.onEach { newPathInfoState ->
                        syncNewPathInfoState(newPathInfoState)
                    }.launchIn(lifecycleScope)

                    observeHidePath.onEach { pathId ->
                        hidePathById(pathId)
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

                    observeNeedToClearMapNow {
                        clearMap()
                    }

                    onInitFinish()
                }
        }
    }

    private fun showConfirmDialog(confirmDialogInfo: ConfirmDialogInfo) {
        context?.let { context ->
            when (confirmDialogInfo.dialogType) {
                ConfirmDialogType.DELETE_PATH -> {
                    DeleteConfirmDialog(
                        context = context,
                        onConfirm = { viewModel.onConfirmPathDelete(confirmDialogInfo.additionalInfo as Long) }).show()
                }

                ConfirmDialogType.GEO_LOCATION_PERMISSION_REQUIRED -> {
                    GeoLocationRequiredDialog(
                        context = context,
                        onConfirm = { viewModel.onLocationPermissionDialogConfirmed() }).show()
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
        viewModel.onResume()
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

    override fun onPause() {
        mapView.onPause()
        viewModel.onPause()
        LocalBroadcastManager
            .getInstance(requireContext())
            .unregisterReceiver(locationChangeReceiver)
        super.onPause()
    }

    private fun updateMapUiState(mapUiState: MapUiState) {
        if (mapUiState.isPathFinished) {
            setLastPathFinished()
        }

        showAllPathsButton.visibility = when (mapUiState.showPathsButtonState) {
            ShowPathsButtonState.GONE -> GONE
            else -> VISIBLE
        }
        showAllPathsDefaultImage.visibility = when (mapUiState.showPathsButtonState) {
            ShowPathsButtonState.DEFAULT -> VISIBLE
            else -> GONE
        }
        showAllPathsHideImage.visibility = when (mapUiState.showPathsButtonState) {
            ShowPathsButtonState.HIDE -> VISIBLE
            else -> GONE
        }
        showAllPathsProgress.visibility = when (mapUiState.showPathsButtonState) {
            ShowPathsButtonState.LOADING -> VISIBLE
            else -> GONE
        }
        showPathsFilterButton.visibility = when (mapUiState.showPathsFilterButtonState) {
            ShowPathsFilterButtonState.GONE -> GONE
            else -> VISIBLE
        }
        showPathsFilterAllInCommonStateImage.visibility =
            when (mapUiState.showPathsFilterButtonState) {
                ShowPathsFilterButtonState.ALL_IN_COMMON_COLOR -> VISIBLE
                else -> GONE
            }
        showPathsFilterRatedOnlyStateImage.visibility =
            when (mapUiState.showPathsFilterButtonState) {
                ShowPathsFilterButtonState.RATED_ONLY -> VISIBLE
                else -> GONE
            }

        when (mapUiState.pathsInfoListState) {
            PathsInfoListState.DEFAULT -> {
                pathsInfoList.visibility = VISIBLE
                pathsInfoProgress.visibility = GONE
                pathsEmptyListError.visibility = GONE
            }

            PathsInfoListState.LOADING -> {
                pathsInfoList.visibility = GONE
                pathsInfoProgress.visibility = VISIBLE
                pathsEmptyListError.visibility = GONE
            }

            PathsInfoListState.EMPTY_LIST -> {
                pathsInfoList.visibility = GONE
                pathsInfoProgress.visibility = GONE
                pathsEmptyListError.visibility = VISIBLE
            }
        }

        when (mapUiState.bottomMenuState) {
            BottomMenuState.DEFAULT -> {
                pathsMenu.visibility = GONE
                walkButtonsHolder.visibility = VISIBLE
            }

            BottomMenuState.PATHS_MENU -> {
                pathsMenu.visibility = VISIBLE
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

    private fun paintNewCommonPath(path: MapCommonPath, color: Int) {
        if (context != null) {
            mapView.overlays.add(Polyline(mapView).apply {
                outlinePaint.color = color

                setPoints(path.pathPoints.map { point ->
                    GeoPoint(
                        point.latitude,
                        point.longitude
                    )
                })

                showingPathsPolylines[path.pathId] = listOf(this)
            })
            refreshMapNow()
        }
    }

    private fun paintNewRatingPath(path: MapRatingPath) {
        val resultPolylineList = ArrayList<Polyline>()
        var lastAddedPolyline: Polyline? = null
        var lastSegment: MapPathSegment? = null
        for (segment in path.pathSegments) {
            if (lastAddedPolyline == null) {
                lastAddedPolyline = createRatingSegmentPolyline(segment)
                lastSegment = segment
                resultPolylineList.add(lastAddedPolyline)
            } else {
                if (lastSegment != null && segment.rating == lastSegment.rating && segment.startPoint == lastSegment.finishPoint) {
                    lastAddedPolyline.addPoint(segment.finishPoint.toGeoPoint())
                } else {
                    lastAddedPolyline = createRatingSegmentPolyline(segment)
                    lastSegment = segment
                    resultPolylineList.add(lastAddedPolyline)
                }
            }
        }
        mapView.overlays.addAll(resultPolylineList)
        showingPathsPolylines[path.pathId] = resultPolylineList
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

    private fun syncNewPathInfoState(newPathInfoState: PathInfoItemState) {
        if (newPathInfoState.pathId == -1L) {
            pathsInfoListAdapter.setAllPathsShowState(newPathInfoState.showButtonState)
        } else {
            if (newPathInfoState.isDeleted) {
                pathsInfoListAdapter.deletePathInfoItem(newPathInfoState.pathId)
            } else {
                pathsInfoListAdapter.setPathShowState(
                    newPathInfoState.pathId,
                    newPathInfoState.showButtonState
                )
            }
        }
    }
}