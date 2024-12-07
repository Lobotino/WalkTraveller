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
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.database.provideDatabase
import ru.lobotino.walktraveller.repositories.DatabasePathRepository
import ru.lobotino.walktraveller.repositories.LastCreatedPathIdRepository
import ru.lobotino.walktraveller.repositories.LocationUpdatesRepository
import ru.lobotino.walktraveller.repositories.PathRatingRepository
import ru.lobotino.walktraveller.repositories.PathWritingNowNotificationRepository
import ru.lobotino.walktraveller.repositories.VibrationRepository
import ru.lobotino.walktraveller.repositories.WritingPathStatesRepository
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.usecases.CurrentPathInteractor
import ru.lobotino.walktraveller.usecases.LocationMediator
import ru.lobotino.walktraveller.usecases.PathRatingUseCase
import ru.lobotino.walktraveller.usecases.PathWritingNowNotificationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.ICurrentPathInteractor
import ru.lobotino.walktraveller.usecases.interfaces.ILocationMediator
import ru.lobotino.walktraveller.usecases.interfaces.INotificationInteractor
import ru.lobotino.walktraveller.utils.APPLICATION_ID
import ru.lobotino.walktraveller.utils.ext.toMapPoint

class WritingPathService : Service() {

    private val binder: IBinder = LocationUpdatesBinder()
    private lateinit var sharedPreferences: SharedPreferences

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
                provideDatabase(applicationContext),
                LastCreatedPathIdRepository(sharedPreferences)
            ),
            PathRatingUseCase(
                PathRatingRepository(sharedPreferences),
                VibrationRepository(applicationContext)
            )
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
                                lightColor = Color.BLUE
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
        locationMediator = LocationMediator()
    }

    private fun onNewLocation(newLocation: Location) {
        Log.d(TAG, "New location: ${newLocation.latitude}, ${newLocation.longitude}")
        locationMediator.onNewLocation(newLocation) { location ->
            if (writingPathStatesRepository.isWritingPathNow()) {
                MainScope().launch {
                    pathInteractor.addNewPathPoint(location.toMapPoint())
                }
            }
        }
    }

    private fun startForegroundNotification() {
        startForeground(
            notificationInteractor.getNotificationId(),
            notificationInteractor.getNotification()
        )
    }

    private fun stopForegroundNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            Log.d(TAG, action)
            when (action) {
                ACTION_START_WRITING_PATH -> startWritingPath()
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
        Log.d(TAG, "onDestroy")
        locationUpdatesRepository.stopLocationUpdates()
        super.onDestroy()
    }

    private fun startWritingPath() {
        locationUpdatesRepository.startLocationUpdates()
        startForegroundNotification()
    }

    fun finishWritingPath() {
        pathInteractor.finishCurrentPath()
        locationUpdatesRepository.stopLocationUpdates()
        stopForegroundNotification()
        stopSelf()
    }

    inner class LocationUpdatesBinder : Binder() {
        val writingPathService: WritingPathService
            get() = this@WritingPathService
    }

    companion object {
        private val TAG = WritingPathService::class.java.simpleName
        private const val CHANNEL_ID = "walk_traveller_notifications_channel"
        const val ACTION_START_WRITING_PATH = "$APPLICATION_ID.start_writing_path"
    }
}
