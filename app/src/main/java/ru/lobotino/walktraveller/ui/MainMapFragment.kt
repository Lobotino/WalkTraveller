package ru.lobotino.walktraveller.ui

import android.content.*
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Room
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import ru.lobotino.walktraveller.App.Companion.PATH_DATABASE_NAME
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.database.AppDatabase
import ru.lobotino.walktraveller.model.MapPath
import ru.lobotino.walktraveller.repositories.DefaultLocationRepository
import ru.lobotino.walktraveller.repositories.GeoPermissionsRepository
import ru.lobotino.walktraveller.repositories.LocalPathRepository
import ru.lobotino.walktraveller.services.LocationUpdatesService
import ru.lobotino.walktraveller.services.LocationUpdatesService.Companion.EXTRA_LOCATION
import ru.lobotino.walktraveller.usecases.LocalMapPathsInteractor
import ru.lobotino.walktraveller.usecases.PermissionsInteractor
import ru.lobotino.walktraveller.viewmodels.MapViewModel

class MainMapFragment : Fragment() {

    private lateinit var mapView: MapView
    private lateinit var viewModel: MapViewModel
    private lateinit var walkStartButton: CardView
    private lateinit var walkStopButton: CardView
    private lateinit var showAllPathsButton: CardView

    private var currentPathPolyline: Polyline? = null

    private var locationUpdatesService: LocationUpdatesService? = null

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
            val location = intent.getParcelableExtra<Location>(EXTRA_LOCATION)
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

            walkStopButton = view.findViewById<CardView>(R.id.walk_stop_button)
                .apply {
                    setOnClickListener { viewModel.onStopPathButtonClicked() }
                }

            showAllPathsButton = view.findViewById<CardView>(R.id.show_all_map_paths)
                .apply {
                    setOnClickListener { viewModel.onShowAllPathsButtonClicked() }
                }

            initViewModel()
        }
    }

    private fun initViewModel() {
        if (activity != null && context != null) {
            viewModel =
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
                    .create(MapViewModel::class.java).apply {
                        setPermissionsInteractor(
                            PermissionsInteractor(
                                GeoPermissionsRepository(
                                    this@MainMapFragment,
                                    requireContext().applicationContext
                                )
                            )
                        )

                        setDefaultLocationRepository(DefaultLocationRepository(requireContext().applicationContext))

                        setMapPathInteractor(
                            LocalMapPathsInteractor(
                                LocalPathRepository(
                                    Room.databaseBuilder(
                                        requireContext().applicationContext,
                                        AppDatabase::class.java, PATH_DATABASE_NAME
                                    ).build()
                                )
                            )
                        )

                        observePermissionsDeniedResult.onEach {
                            showPermissionsDeniedError()
                        }.launchIn(lifecycleScope)

                        observeNewPathSegment.onEach { pathSegment ->
                            paintNewPathLine(
                                GeoPoint(pathSegment.first.latitude, pathSegment.first.longitude),
                                GeoPoint(pathSegment.second.latitude, pathSegment.second.longitude)
                            )
                        }.launchIn(lifecycleScope)

                        observeNewPath.onEach { path ->
                            paintNewCommonPath(path)
                        }.launchIn(lifecycleScope)

                        observeMapUiState.onEach { mapUiState -> updateMapUiState(mapUiState) }
                            .launchIn(lifecycleScope)

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
            locationUpdatesService?.startLocationUpdates()
            walkStartButton.visibility = View.GONE
            walkStopButton.visibility = View.VISIBLE
        } else {
            walkStartButton.visibility = View.VISIBLE
            walkStopButton.visibility = View.GONE
        }

        if (mapUiState.isPathFinished) {
            locationUpdatesService?.stopLocationUpdates()
            setLastPathFinished()
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

    private fun paintNewCommonPath(path: MapPath) {
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

    private fun setLastPathFinished() {
        currentPathPolyline?.outlinePaint?.color =
            ContextCompat.getColor(requireContext(), R.color.finished_path)

        currentPathPolyline = null
    }

    private fun refreshMapNow() {
        mapView.controller.setCenter(mapView.mapCenter)
    }
}