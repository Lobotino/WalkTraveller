package ru.lobotino.walktraveller.services

import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.lobotino.walktraveller.repositories.LocationUpdatesRepository
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesRepository
import ru.lobotino.walktraveller.usecases.LocationMediator
import ru.lobotino.walktraveller.usecases.interfaces.ILocationMediator
import ru.lobotino.walktraveller.utils.APPLICATION_ID

class UserLocationUpdatesService : Service() {
    private val binder: IBinder = LocationUpdatesBinder()

    private lateinit var locationUpdatesRepository: ILocationUpdatesRepository
    private lateinit var locationMediator: ILocationMediator

    override fun onCreate() {
        super.onCreate()
        initLocationUpdatesRepository()
        initLocationMediator()
    }

    private fun initLocationUpdatesRepository() {
        locationUpdatesRepository = LocationUpdatesRepository(
            LocationServices.getFusedLocationProviderClient(this),
            5000
        ).apply {
            observeLocationUpdates().onEach { location ->
                onNewLocation(location)
            }.launchIn(CoroutineScope(Dispatchers.Default))
        }
    }

    private fun initLocationMediator() {
        locationMediator = LocationMediator()
    }

    private fun onNewLocation(newLocation: Location) {
        Log.d(TAG, "New location: ${newLocation.latitude}, ${newLocation.longitude}")
        locationMediator.onNewLocation(newLocation) { location ->
            LocalBroadcastManager.getInstance(applicationContext)
                .sendBroadcast(
                    Intent(ACTION_BROADCAST).apply {
                        putExtra(EXTRA_LOCATION, location)
                    }
                )
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "Client bound to service")
        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "Last client unbound from service")
        return true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            Log.d(TAG, action)
            when (action) {
                ACTION_START_LOCATION_UPDATES -> {
                    startLocationUpdates()
                }

                else -> {}
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stopLocationUpdates()
        super.onDestroy()
    }

    fun startLocationUpdates() {
        // FIXME twice requesting after onResume
        Log.i(TAG, "Requesting location updates")
        locationUpdatesRepository.startLocationUpdates()
    }

    private fun stopLocationUpdates() {
        Log.i(TAG, "Removing location updates")
        try {
            locationUpdatesRepository.stopLocationUpdates()
        } catch (unlikely: SecurityException) {
            Log.e(
                TAG,
                "Lost location permission. Could not remove updates. $unlikely"
            )
        }
    }

    inner class LocationUpdatesBinder : Binder() {
        val locationUpdatesService: UserLocationUpdatesService
            get() = this@UserLocationUpdatesService
    }

    companion object {
        private val TAG = UserLocationUpdatesService::class.java.simpleName
        const val ACTION_START_LOCATION_UPDATES = "$APPLICATION_ID.start_location_updates"
        const val ACTION_BROADCAST = "$APPLICATION_ID.broadcast"
        const val EXTRA_LOCATION = "$APPLICATION_ID.location"
    }
}
