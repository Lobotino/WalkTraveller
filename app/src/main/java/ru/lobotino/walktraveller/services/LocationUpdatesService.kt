package ru.lobotino.walktraveller.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
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
import ru.lobotino.walktraveller.BuildConfig
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.database.AppDatabase
import ru.lobotino.walktraveller.repositories.DatabasePathRepository
import ru.lobotino.walktraveller.repositories.LastCreatedPathIdRepository
import ru.lobotino.walktraveller.repositories.LocationUpdatesRepository
import ru.lobotino.walktraveller.repositories.PathRatingRepository
import ru.lobotino.walktraveller.repositories.PathWritingNowNotificationRepository
import ru.lobotino.walktraveller.repositories.WritingPathStatesRepository
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.usecases.CurrentPathInteractor
import ru.lobotino.walktraveller.usecases.LocationMediator
import ru.lobotino.walktraveller.usecases.PathWritingNowNotificationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.ICurrentPathInteractor
import ru.lobotino.walktraveller.usecases.interfaces.ILocationMediator
import ru.lobotino.walktraveller.usecases.interfaces.INotificationInteractor
import ru.lobotino.walktraveller.utils.ext.toMapPoint


class LocationUpdatesService : Service() {

    companion object {
        private val TAG = LocationUpdatesService::class.java.simpleName
        private const val APPLICATION_ID = BuildConfig.APPLICATION_ID
        private const val CHANNEL_ID = "walk_traveller_notifications_channel"
        const val EXTRA_LOCATION = "$APPLICATION_ID.location"
        const val ACTION_BROADCAST = "$APPLICATION_ID.broadcast"
        const val ACTION_START_LOCATION_UPDATES = "$APPLICATION_ID.start_location_updates"
        const val ACTION_STOP_LOCATION_UPDATES = "$APPLICATION_ID.stop_location_updates"
        const val ACTION_FINISH_WRITING_PATH = "$APPLICATION_ID.finish_writing_path"
    }

    private val binder: IBinder = LocationUpdatesBinder()
    private lateinit var sharedPreferences: SharedPreferences

    private var lastLocation: Location? = null

    private lateinit var writingPathStatesRepository: IWritingPathStatesRepository
    private lateinit var locationUpdatesRepository: ILocationUpdatesRepository
    private lateinit var notificationInteractor: INotificationInteractor
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
                LastCreatedPathIdRepository(sharedPreferences)
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
                onNewLocation(location)
            }.launchIn(CoroutineScope(Dispatchers.Default))
        }
    }

    private fun initLocationNotificationInteractor() {
        notificationInteractor =
            PathWritingNowNotificationInteractor(
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
                PathWritingNowNotificationRepository(
                    applicationContext,
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

    private fun onNewLocation(newLocation: Location) {
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
        }
    }

    private fun startForegroundNotification() {
        startForeground(
            notificationInteractor.getNotificationId(),
            notificationInteractor.getNotification()
        )
    }

    private fun stopForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            Log.d(TAG, action)
            when (action) {
                ACTION_START_LOCATION_UPDATES -> {
                    startLocationUpdates()
                }

                ACTION_STOP_LOCATION_UPDATES -> {
                    stopLocationUpdates()
                    stopSelf()
                }

                ACTION_FINISH_WRITING_PATH -> {
                    finishWritingPath()
                }

                else -> {}
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "Client bound to service")
        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "Last client unbound from service")
        return true
    }

    override fun onDestroy() {
        locationUpdatesRepository.stopLocationUpdates()
        super.onDestroy()
    }

    fun startLocationUpdates() {
        Log.i(TAG, "Requesting location updates")
        locationUpdatesRepository.startLocationUpdates()
    }

    fun stopLocationUpdates() {
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

    fun startWritingPath() {
        startForegroundNotification()
    }

    fun finishWritingPath() {
        pathInteractor.finishCurrentPath()
        stopForegroundNotification()
    }

    inner class LocationUpdatesBinder : Binder() {
        val locationUpdatesService: LocationUpdatesService
            get() = this@LocationUpdatesService
    }
}