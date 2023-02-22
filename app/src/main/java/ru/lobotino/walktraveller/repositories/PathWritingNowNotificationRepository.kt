package ru.lobotino.walktraveller.repositories

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.repositories.interfaces.INotificationRepository

class PathWritingNowNotificationRepository(
    private val appContext: Context,
    private val notificationChannelId: String
) : INotificationRepository {

    companion object {
        private const val NOTIFICATION_ID = 110011
    }

    private val notificationTitleText: String =
        appContext.getString(R.string.notification_path_tracking_title)

    private val notificationInfoText: String =
        appContext.getString(R.string.notification_path_tracking_info)

    override fun getNotificationId(): Int {
        return NOTIFICATION_ID
    }

    override fun getNotification(): Notification {
        return NotificationCompat.Builder(appContext, notificationChannelId)
            .setContentText(notificationInfoText)
            .setContentTitle(notificationTitleText)
            .setOngoing(true)
            .setPriority(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) NotificationManager.IMPORTANCE_DEFAULT else Notification.PRIORITY_DEFAULT)
            .setSmallIcon(R.drawable.ic_path)
            .setTicker(notificationInfoText).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setChannelId(notificationChannelId)
                }
            }.build()
    }
}