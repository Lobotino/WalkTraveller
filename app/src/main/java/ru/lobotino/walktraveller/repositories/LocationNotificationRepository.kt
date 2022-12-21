package ru.lobotino.walktraveller.repositories

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.provider.ContactsContract.Directory.PACKAGE_NAME
import androidx.core.app.NotificationCompat
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.repositories.interfaces.ILocationNotificationRepository
import java.lang.ref.WeakReference
import java.text.DateFormat
import java.util.*

class LocationNotificationRepository(
    private val appContext: Context,
    localContext: Context,
    private val notificationServiceClass: Class<out Context>,
    private val notificationActivityClass: Class<out Context>,
    private val notificationChannelId: String
) : ILocationNotificationRepository {

    companion object {
        const val EXTRA_STARTED_FROM_NOTIFICATION =
            "${PACKAGE_NAME}.started_from_notification"

        private const val NOTIFICATION_ID = 110011
    }

    private val weakActivityContext = WeakReference(localContext)

    override fun getNotificationId(): Int {
        return NOTIFICATION_ID
    }

    override fun getLocationNotification(location: Location?): Notification {
        val notificationText: CharSequence = getLocationText(location)

        return NotificationCompat.Builder(appContext, notificationChannelId)
            .addAction(
                R.drawable.ic_launcher_foreground, appContext.getString(R.string.app_name),
                getPendingActivityIntent(weakActivityContext.get())
            )
            .addAction(
                R.drawable.ic_launcher_foreground, appContext.getString(R.string.app_name),
                getPendingServiceIntent()
            )
            .setContentText(notificationText)
            .setContentTitle(getLocationTitle(appContext))
            .setOngoing(true)
            .setPriority(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) NotificationManager.IMPORTANCE_HIGH else Notification.PRIORITY_HIGH)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker(notificationText)
            .setWhen(System.currentTimeMillis()).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setChannelId(notificationChannelId)
                }
            }.build()
    }

    private fun getPendingActivityIntent(context: Context?): PendingIntent {
        return PendingIntent.getActivity(
            appContext, 0,
            Intent(context ?: appContext, notificationActivityClass), when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> PendingIntent.FLAG_IMMUTABLE
                else -> 0
            }
        )
    }

    private fun getPendingServiceIntent(): PendingIntent {
        return PendingIntent.getService(
            appContext, 0, Intent(appContext, notificationServiceClass).apply {
                putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)
            },
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                else -> PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
    }

    private fun getLocationText(location: Location?): String {
        return if (location == null) "Unknown location" else "(" + location.latitude
            .toString() + ", " + location.longitude.toString() + ")"
    }

    private fun getLocationTitle(context: Context): String {
        return context.getString(
            R.string.location_updated,
            DateFormat.getDateTimeInstance().format(Date())
        )
    }
}