package ru.lobotino.walktraveller.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.location.Location
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
import ru.lobotino.walktraveller.repositories.DatabasePathRepository
import ru.lobotino.walktraveller.repositories.LocationNotificationRepository
import ru.lobotino.walktraveller.repositories.LocationUpdatesRepository
import ru.lobotino.walktraveller.repositories.PathRatingRepository
import ru.lobotino.walktraveller.repositories.WritingPathStatesRepository
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.ui.MainActivity
import ru.lobotino.walktraveller.usecases.CurrentPathInteractor
import ru.lobotino.walktraveller.usecases.LocationMediator
import ru.lobotino.walktraveller.usecases.LocationNotificationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.ICurrentPathInteractor
import ru.lobotino.walktraveller.usecases.interfaces.ILocationMediator
import ru.lobotino.walktraveller.usecases.interfaces.ILocationNotificationInteractor
import ru.lobotino.walktraveller.utils.ext.toMapPoint


class LocationUpdatesService : Service() {

    companion object {
        private val TAG = LocationUpdatesService::class.java.simpleName
        private const val PACKAGE_NAME = ContactsContract.Directory.PACKAGE_NAME
        private const val CHANNEL_ID = "walk_traveller_notifications_channel"
        const val EXTRA_LOCATION = "$PACKAGE_NAME.location"
        const val ACTION_BROADCAST = "$PACKAGE_NAME.broadcast"
        const val ACTION_START_LOCATION_UPDATES = "$PACKAGE_NAME.start_location_updates"
        const val ACTION_STOP_LOCATION_UPDATES = "$PACKAGE_NAME.stop_location_updates"
        const val ACTION_FINISH_CURRENT_PATH = "$PACKAGE_NAME.finish_current_path"
    }

    private lateinit var sharedPreferences: SharedPreferences

    private var lastLocation: Location? = null

    private lateinit var writingPathStatesRepository: IWritingPathStatesRepository
    private lateinit var locationUpdatesRepository: ILocationUpdatesRepository
    private lateinit var locationNotificationInteractor: ILocationNotificationInteractor
    private lateinit var locationMediator: ILocationMediator
    private lateinit var pathInteractor: ICurrentPathInteractor

    override fun onCreate() {
        super.onCreate()
        initSharedPreferences()
        initLocationMediator()
        initLocationNotificationInteractor()
        initWritingPathStatesRepository()
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
            DatabasePathRepository(
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
            LocationServices.getFusedLocationProviderClient(this),
            5000
        ).apply {
            observeLocationUpdates().onEach { location ->
                onNewPathLocation(location)
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
                            ).apply {
                                description = "Location updates for saving current user path"
                                enableLights(true)
                                lightColor = Color.BLUE;
                            }
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

    private fun initWritingPathStatesRepository() {
        this.writingPathStatesRepository = WritingPathStatesRepository(sharedPreferences)
    }

    private fun initLocationMediator() {
        locationMediator = LocationMediator(lastLocation)
    }

    private fun onNewPathLocation(newLocation: Location) {
        Log.d(TAG, "New location: ${newLocation.latitude}, ${newLocation.longitude}")
        locationMediator.onNewLocation(newLocation) { location ->
            lastLocation = location

            if (writingPathStatesRepository.isWritingPathNow()) {
                CoroutineScope(Dispatchers.Default).launch {
                    pathInteractor.addNewPathPoint(location.toMapPoint())
                }
            }

            LocalBroadcastManager.getInstance(applicationContext)
                .sendBroadcast(Intent(ACTION_BROADCAST).apply {
                    putExtra(EXTRA_LOCATION, location)
                })

            locationNotificationInteractor.showOnLocationChangeNotification(location)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        intent.action?.let { action ->
            when (action) {
                ACTION_START_LOCATION_UPDATES -> {
                    startLocationUpdates()
                    return START_STICKY
                }

                ACTION_STOP_LOCATION_UPDATES -> {
                    stopLocationUpdates()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        stopForeground(true)
                    }
                    stopSelf()
                    return START_STICKY
                }

                ACTION_FINISH_CURRENT_PATH -> {
                    finishCurrentPath()
                    return START_STICKY
                }

                else -> {}
            }
        }

        startForeground(
            locationNotificationInteractor.getNotificationId(),
            locationNotificationInteractor.getNotification(lastLocation)
        )

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        locationUpdatesRepository.stopLocationUpdates()
        super.onDestroy()
    }

    private fun startLocationUpdates() {
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

    private fun finishCurrentPath() {
        pathInteractor.finishCurrentPath()
    }
}