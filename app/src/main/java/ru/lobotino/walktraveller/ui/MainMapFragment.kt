package ru.lobotino.walktraveller.ui

import android.content.*
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Room
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.App.Companion.PATH_DATABASE_NAME
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.database.AppDatabase
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.SegmentRating.*
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.*
import ru.lobotino.walktraveller.services.LocationUpdatesService
import ru.lobotino.walktraveller.services.LocationUpdatesService.Companion.EXTRA_LOCATION
import ru.lobotino.walktraveller.ui.model.MapUiState
import ru.lobotino.walktraveller.ui.model.ShowPathsButtonState
import ru.lobotino.walktraveller.usecases.LocalMapPathsInteractor
import ru.lobotino.walktraveller.usecases.PermissionsInteractor
import ru.lobotino.walktraveller.utils.ext.toGeoPoint
import ru.lobotino.walktraveller.viewmodels.MapViewModel

class MainMapFragment : Fragment() {

    private lateinit var mapView: MapView
    private lateinit var viewModel: MapViewModel
    private lateinit var walkStartButton: CardView
    private lateinit var walkStopButton: CardView
    private lateinit var ratingBadlyButton: CardView
    private lateinit var ratingNormalButton: CardView
    private lateinit var ratingGoodButton: CardView
    private lateinit var ratingPerfectButton: CardView
    private lateinit var walkStopAcceptProgress: CircularProgressIndicator
    private lateinit var showPathsButton: CardView
    private lateinit var showPathsProgress: CircularProgressIndicator
    private lateinit var showPathsDefaultImage: ImageView

    private var currentPathPolyline: Polyline? = null

    private var locationUpdatesService: LocationUpdatesService? = null

    private var stopAcceptProgressJob: Job? = null

    private lateinit var sharedPreferences: SharedPreferences

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            locationUpdatesService = (service as LocationUpdatesService.LocalBinder).service
            viewModel.onGeoLocationUpdaterConnected()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            viewModel.onGeoLocationUpdaterDisconnected()
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false).also { view ->
            initViews(view)
            initViewModel()
        }
    }

    private fun initViews(rootLayout: View) {
        rootLayout.let { view ->
            val mapViewContainer = view.findViewById<FrameLayout>(R.id.map_view_container)

            mapView = MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                controller.setZoom(12.5)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setMultiTouchControls(true)
            }
            mapViewContainer.addView(mapView)

            walkStartButton = view.findViewById<CardView>(R.id.walk_start_button)
                .apply {
                    setOnClickListener { viewModel.onStartPathButtonClicked() }
                }

            ratingBadlyButton = view.findViewById<CardView>(R.id.rating_badly)
                .apply {
                    setOnClickListener { viewModel.onRatingButtonClicked(BADLY) }
                }

            ratingNormalButton = view.findViewById<CardView>(R.id.rating_normal)
                .apply {
                    setOnClickListener { viewModel.onRatingButtonClicked(NORMAL) }
                }

            ratingGoodButton = view.findViewById<CardView>(R.id.rating_good)
                .apply {
                    setOnClickListener { viewModel.onRatingButtonClicked(GOOD) }
                }

            ratingPerfectButton = view.findViewById<CardView>(R.id.rating_perfect)
                .apply {
                    setOnClickListener { viewModel.onRatingButtonClicked(PERFECT) }
                }

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

            showPathsButton = view.findViewById<CardView>(R.id.show_paths_button)
                .apply {
                    setOnClickListener { viewModel.onShowAllPathsButtonClicked() }
                }

            showPathsProgress = view.findViewById(R.id.show_paths_progress)
            showPathsDefaultImage = view.findViewById(R.id.show_paths_default_image)
        }
    }

    private fun initViewModel() {
        if (activity != null && context != null) {
            viewModel =
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
                    .create(MapViewModel::class.java).apply {
                        sharedPreferences = requireContext().getSharedPreferences(
                            App.SHARED_PREFS_TAG,
                            AppCompatActivity.MODE_PRIVATE
                        )

                        setPermissionsInteractor(
                            PermissionsInteractor(
                                GeoPermissionsRepository(
                                    this@MainMapFragment,
                                    requireContext().applicationContext
                                )
                            )
                        )

                        setMapPathInteractor(
                            LocalMapPathsInteractor(
                                LocalPathRepository(
                                    Room.databaseBuilder(
                                        requireContext().applicationContext,
                                        AppDatabase::class.java, PATH_DATABASE_NAME
                                    ).build(),
                                    sharedPreferences
                                )
                            )
                        )

                        setDefaultLocationRepository(DefaultLocationRepository(sharedPreferences))

                        setLocationUpdatesStatesRepository(
                            LocationUpdatesStatesRepository(
                                sharedPreferences
                            )
                        )

                        setPathRatingRepository(PathRatingRepository(sharedPreferences))

                        observePermissionsDeniedResult.onEach {
                            showPermissionsDeniedError()
                        }.launchIn(lifecycleScope)

                        observeNewPathSegment.onEach { pathSegment ->
                            paintNewPathLine(
                                pathSegment.startPoint.toGeoPoint(),
                                pathSegment.finishPoint.toGeoPoint()
                            )
                        }.launchIn(lifecycleScope)

                        observeNewCommonPath.onEach { path ->
                            paintNewCommonPath(path)
                        }.launchIn(lifecycleScope)

                        observeNewRatingPath.onEach { path ->
                            paintNewRatingPath(path)
                        }.launchIn(lifecycleScope)

                        observeMapUiState.onEach { mapUiState ->
                            updateMapUiState(mapUiState)
                        }.launchIn(lifecycleScope)

                        observeRegularLocationUpdate.onEach { needToUpdateLocation ->
                            if (needToUpdateLocation) {
                                locationUpdatesService?.startLocationUpdates()
                            } else {
                                locationUpdatesService?.stopLocationUpdates()
                            }
                        }.launchIn(lifecycleScope)

                        onInitFinish()
                    }
        }
    }

    override fun onStart() {
        activity?.bindService(
            Intent(activity, LocationUpdatesService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        super.onStart()
    }

    override fun onStop() {
        activity?.unbindService(serviceConnection)
        super.onStop()
    }

    override fun onResume() {
        mapView.onResume()
        viewModel.updateNewPointsIfNeeded()
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            locationChangeReceiver,
            IntentFilter(LocationUpdatesService.ACTION_BROADCAST)
        )
        super.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        LocalBroadcastManager
            .getInstance(requireContext())
            .unregisterReceiver(locationChangeReceiver)
        super.onPause()
    }

    private fun updateMapUiState(mapUiState: MapUiState) {
        if (mapUiState.needToClearMapNow) {
            clearMap()
        }

        if (mapUiState.isWritePath) {
            walkStartButton.visibility = GONE
            walkStopButton.visibility = VISIBLE
            ratingBadlyButton.visibility = VISIBLE
            ratingNormalButton.visibility = VISIBLE
            ratingGoodButton.visibility = VISIBLE
            ratingPerfectButton.visibility = VISIBLE
        } else {
            walkStartButton.visibility = VISIBLE
            walkStopButton.visibility = GONE
            ratingBadlyButton.visibility = GONE
            ratingNormalButton.visibility = GONE
            ratingGoodButton.visibility = GONE
            ratingPerfectButton.visibility = GONE
        }

        if (mapUiState.isPathFinished) {
            setLastPathFinished()
        }

        when (mapUiState.showPathsButtonState) {
            ShowPathsButtonState.DEFAULT -> {
                showPathsDefaultImage.visibility = VISIBLE
                showPathsProgress.visibility = GONE
            }
            ShowPathsButtonState.LOADING -> {
                showPathsDefaultImage.visibility = GONE
                showPathsProgress.visibility = VISIBLE
            }
        }

        if (mapUiState.mapCenter != null) {
            mapView.controller?.setCenter(
                GeoPoint(
                    mapUiState.mapCenter.latitude,
                    mapUiState.mapCenter.longitude
                )
            )
        } else {
            refreshMapNow()
        }
    }

    private fun clearMap() {
        mapView.overlays.clear()
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

    private fun paintNewCommonPath(path: MapCommonPath) {
        if (context != null) {
            mapView.overlays.add(Polyline(mapView).apply {
                outlinePaint.color =
                    ContextCompat.getColor(requireContext(), R.color.common_path)

                setPoints(path.pathPoints.map { point ->
                    GeoPoint(
                        point.latitude,
                        point.longitude
                    )
                })
            })
        }
    }

    private fun paintNewRatingPath(path: MapRatingPath) {
        val resultPolylineList = ArrayList<Polyline>()
        var lastAddedPolyline: Polyline? = null
        var lastSegmentRating: SegmentRating? = null
        for (segment in path.pathSegments) {
            if (lastAddedPolyline == null) {
                lastAddedPolyline = createRatingSegmentPolyline(segment)
                lastSegmentRating = segment.rating
                resultPolylineList.add(lastAddedPolyline)
            } else {
                if (segment.rating == lastSegmentRating) {
                    lastAddedPolyline.addPoint(segment.finishPoint.toGeoPoint())
                } else {
                    lastAddedPolyline = createRatingSegmentPolyline(segment)
                    lastSegmentRating = segment.rating
                    resultPolylineList.add(lastAddedPolyline)
                }
            }
        }
        mapView.overlays.addAll(resultPolylineList)
    }

    private fun createRatingSegmentPolyline(pathSegment: MapPathSegment): Polyline {
        return Polyline(mapView).apply {
            outlinePaint.apply {
                addPoint(pathSegment.startPoint.toGeoPoint())
                addPoint(pathSegment.finishPoint.toGeoPoint())
                getRatingColor(pathSegment.rating)?.let { ratingColor ->
                    color = ratingColor
                }
            }
        }
    }

    private fun paintNewPathLine(from: GeoPoint, to: GeoPoint) {
        if (context != null) {
            if (currentPathPolyline == null) {
                currentPathPolyline = Polyline(mapView).apply {
                    outlinePaint.apply {
                        color = ContextCompat.getColor(requireContext(), R.color.current_path)
                    }
                }
                mapView.overlays.add(currentPathPolyline)
            }

            currentPathPolyline!!.apply {
                if (points.isEmpty()) {
                    addPoint(from)
                }
                addPoint(to)
            }.also {
                refreshMapNow()
            }
        }
    }

    @ColorInt
    private fun getRatingColor(segmentRating: SegmentRating): Int? {
        context?.let { context ->
            when (segmentRating) {
                BADLY -> return@getRatingColor ContextCompat.getColor(context, R.color.rating_badly)
                NORMAL -> return@getRatingColor ContextCompat.getColor(
                    context,
                    R.color.rating_normal
                )
                GOOD -> return@getRatingColor ContextCompat.getColor(context, R.color.rating_good)
                PERFECT -> return@getRatingColor ContextCompat.getColor(
                    context,
                    R.color.rating_perfect
                )
            }
        }
        return null
    }

    private fun setLastPathFinished() {
        currentPathPolyline?.outlinePaint?.color =
            ContextCompat.getColor(requireContext(), R.color.finished_path)

        currentPathPolyline = null
    }

    private fun refreshMapNow() {
        mapView.controller.setCenter(mapView.mapCenter)
    }

    private fun walkStopAcceptProgressStart(onFinished: () -> Unit) {
        stopAcceptProgressJob = CoroutineScope(Dispatchers.Default).launch {
            while (walkStopAcceptProgress.progress != 100) {
                delay(9)
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
}