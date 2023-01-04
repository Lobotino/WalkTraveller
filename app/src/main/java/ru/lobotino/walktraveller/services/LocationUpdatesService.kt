package ru.lobotino.walktraveller.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Room
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.App.Companion.PATH_DATABASE_NAME
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.database.AppDatabase
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.*
import ru.lobotino.walktraveller.repositories.LocationNotificationRepository.Companion.EXTRA_STARTED_FROM_NOTIFICATION
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesRepository
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesStatesRepository
import ru.lobotino.walktraveller.ui.MainActivity
import ru.lobotino.walktraveller.usecases.CurrentPathInteractor
import ru.lobotino.walktraveller.usecases.LocationMediator
import ru.lobotino.walktraveller.usecases.LocationNotificationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.ICurrentPathInteractor
import ru.lobotino.walktraveller.usecases.interfaces.ILocationMediator
import ru.lobotino.walktraveller.usecases.interfaces.ILocationNotificationInteractor


class LocationUpdatesService : Service() {

    companion object {
        private val TAG = LocationUpdatesService::class.java.simpleName
        private const val PACKAGE_NAME = ContactsContract.Directory.PACKAGE_NAME
        private const val CHANNEL_ID = "walk_traveller_notifications_channel"
        const val EXTRA_LOCATION = "$PACKAGE_NAME.location"
        const val ACTION_BROADCAST = "$PACKAGE_NAME.broadcast"
    }

    private val binder: IBinder = LocalBinder()
    private lateinit var sharedPreferences: SharedPreferences

    private var changingConfiguration = false
    private var lastLocation: Location? = null

    private lateinit var locationUpdatesStatesRepository: ILocationUpdatesStatesRepository
    private lateinit var locationUpdatesRepository: ILocationUpdatesRepository
    private lateinit var locationNotificationInteractor: ILocationNotificationInteractor
    private lateinit var locationMediator: ILocationMediator
    private lateinit var pathInteractor: ICurrentPathInteractor

    override fun onCreate() {
        super.onCreate()
        initSharedPreferences()
        initLocationMediator()
        initLocationNotificationInteractor()
        initLocationUpdatesStatesRepository()
        initLocationUpdatesRepository()
        initLocalPathRepository()
    }

    private fun initSharedPreferences() {
        sharedPreferences = applicationContext.getSharedPreferences(
            App.SHARED_PREFS_TAG,
            AppCompatActivity.MODE_PRIVATE
        )
    }

    private fun initLocalPathRepository() {
        pathInteractor = CurrentPathInteractor(
            LocalPathRepository(
                Room.databaseBuilder(
                    applicationContext,
                    AppDatabase::class.java, PATH_DATABASE_NAME
                ).build(),
                sharedPreferences
            ),
            PathRatingRepository(sharedPreferences)
        )
    }

    private fun initLocationUpdatesRepository() {
        locationUpdatesRepository = LocationUpdatesRepository(
            LocationServices.getFusedLocationProviderClient(this)
        ).apply {
            observeLocationUpdates().onEach { location ->
                onNewLocation(location)
            }.launchIn(CoroutineScope(Dispatchers.Default))

            observeLocationUpdatesErrors().onEach {
                locationUpdatesStatesRepository.setRequestingLocationUpdates(false)
            }.launchIn(CoroutineScope(Dispatchers.Default))
        }
    }

    private fun initLocationNotificationInteractor() {
        locationNotificationInteractor =
            LocationNotificationInteractor(
                this,
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        createNotificationChannel(
                            NotificationChannel(
                                CHANNEL_ID,
                                getString(R.string.app_name),
                                NotificationManager.IMPORTANCE_DEFAULT
                            )
                        )
                    }
                },
                LocationNotificationRepository(
                    applicationContext,
                    this,
                    LocationUpdatesService::class.java,
                    MainActivity::class.java,
                    CHANNEL_ID
                )
            )
    }

    private fun initLocationUpdatesStatesRepository() {
        this.locationUpdatesStatesRepository = LocationUpdatesStatesRepository(sharedPreferences)
    }

    private fun initLocationMediator() {
        locationMediator = LocationMediator(lastLocation)
    }

    private fun onNewLocation(newLocation: Location) {
        Log.d(TAG, "New location: ${newLocation.latitude}, ${newLocation.longitude}")
        locationMediator.onNewLocation(newLocation) { location ->
            lastLocation = location

            CoroutineScope(Dispatchers.Default).launch {
                pathInteractor.addNewPathPoint(MapPoint(location.latitude, location.longitude))
            }

            LocalBroadcastManager.getInstance(applicationContext)
                .sendBroadcast(Intent(ACTION_BROADCAST).apply {
                    putExtra(EXTRA_LOCATION, location)
                })

            locationNotificationInteractor.showOnLocationChangeNotification(location)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val startedFromNotification = intent.getBooleanExtra(
            EXTRA_STARTED_FROM_NOTIFICATION,
            false
        )

        if (startedFromNotification) {
            stopLocationUpdates()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        changingConfiguration = true
    }

    override fun onBind(intent: Intent): IBinder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        changingConfiguration = false
        return binder
    }

    override fun onRebind(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        changingConfiguration = false
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "Last client unbound from service")

        if (!changingConfiguration && locationUpdatesStatesRepository.isRequestingLocationUpdates()) {
            Log.i(TAG, "Starting foreground service")
            startForeground(
                locationNotificationInteractor.getNotificationId(),
                locationNotificationInteractor.getNotification(lastLocation)
            )
        }
        return true
    }

    override fun onDestroy() {
        locationUpdatesStatesRepository.setRequestingLocationUpdates(false)
        super.onDestroy()
    }

    fun updateLocationNow() {
        locationUpdatesRepository.updateLocationNow()
    }

    fun startLocationUpdates() {
        Log.i(TAG, "Requesting location updates")
        startService(Intent(applicationContext, LocationUpdatesService::class.java))
        locationUpdatesRepository.startLocationUpdates()
        locationUpdatesStatesRepository.setRequestingLocationUpdates(true)
    }

    fun stopLocationUpdates() {
        Log.i(TAG, "Removing location updates")
        try {
            pathInteractor.finishCurrentPath()
            locationUpdatesRepository.stopLocationUpdates()
            locationUpdatesStatesRepository.setRequestingLocationUpdates(false)
            stopSelf()
        } catch (unlikely: SecurityException) {
            locationUpdatesStatesRepository.setRequestingLocationUpdates(true)
            Log.e(
                TAG,
                "Lost location permission. Could not remove updates. $unlikely"
            )
        }
    }

    inner class LocalBinder : Binder() {
        val service: LocationUpdatesService
            get() = this@LocationUpdatesService
    }
}