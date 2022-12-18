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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.repositories.GeoPermissionsRepository
import ru.lobotino.walktraveller.repositories.PathInteractor
import ru.lobotino.walktraveller.services.LocationUpdatesService
import ru.lobotino.walktraveller.services.LocationUpdatesService.Companion.EXTRA_LOCATION
import ru.lobotino.walktraveller.usecases.PermissionsInteractor
import ru.lobotino.walktraveller.viewmodels.MapViewModel

class MainMapFragment : Fragment() {

    private var mapView: MapView? = null

    private lateinit var mapViewModel: MapViewModel

    private var locationUpdatesService: LocationUpdatesService? = null

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            locationUpdatesService = (service as LocationUpdatesService.LocalBinder).service
            mapViewModel.onGeoLocationUpdaterConnected()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mapViewModel.onGeoLocationUpdaterDisconnected()
            locationUpdatesService = null
        }
    }

    private val locationChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(EXTRA_LOCATION)
            if (location != null) {
                mapViewModel.onNewLocationReceive(location)
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
                setMultiTouchControls(true);
            }
            mapViewContainer.addView(mapView)

            initViewModel()
        }
    }

    private fun initViewModel() {
        if (activity != null && context != null) {
            mapViewModel =
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

                        setPathInteractor(PathInteractor())

                        observePermissionsDeniedResult.onEach {
                            showPermissionsDeniedError()
                        }.launchIn(lifecycleScope)

                        observeGeoLocationUpdateState.onEach { geoLocationUpdate ->
                            if (geoLocationUpdate) {
                                locationUpdatesService?.startLocationUpdates()
                            } else {
                                locationUpdatesService?.stopLocationUpdates()
                            }
                        }.launchIn(lifecycleScope)

                        observeLocationUpdate.onEach { newLocation ->
                            mapView?.overlays?.add(Marker(mapView).apply {
                                position = GeoPoint(newLocation.first, newLocation.second)
                            })
                        }.launchIn(lifecycleScope)

                        observeMapCenterUpdate.onEach { newCenter ->
                            mapView?.controller?.setCenter(
                                GeoPoint(
                                    newCenter.first,
                                    newCenter.second
                                )
                            )
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
        mapView?.onResume()
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            locationChangeReceiver,
            IntentFilter(LocationUpdatesService.ACTION_BROADCAST)
        )
        super.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        LocalBroadcastManager
            .getInstance(requireContext())
            .unregisterReceiver(locationChangeReceiver)
        super.onPause()
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
}