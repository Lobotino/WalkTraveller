package ru.lobotino.walktraveller.usecases

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.location.Location
import ru.lobotino.walktraveller.repositories.interfaces.ILocationNotificationRepository
import ru.lobotino.walktraveller.usecases.interfaces.ILocationNotificationInteractor
import java.lang.ref.WeakReference

class LocationNotificationInteractor(
    context: Context,
    private val notificationManager: NotificationManager,
    private val locationNotificationRepository: ILocationNotificationRepository
) : ILocationNotificationInteractor {

    private val weakContext = WeakReference(context)

    override fun showOnLocationChangeNotification(location: Location?) {
        val context = weakContext.get()
        if (context != null) {
            if (serviceIsRunningInForeground(context)) {
                notificationManager.notify(
                    locationNotificationRepository.getNotificationId(),
                    locationNotificationRepository.getLocationNotification(location)
                )
            }
        }
    }

    override fun getNotificationId(): Int {
        return locationNotificationRepository.getNotificationId()
    }

    override fun getNotification(location: Location?): Notification {
        return locationNotificationRepository.getLocationNotification(location)
    }

    private fun serviceIsRunningInForeground(context: Context): Boolean {
        for (service in (context.getSystemService(
            Service.ACTIVITY_SERVICE
        ) as ActivityManager).getRunningServices(Int.MAX_VALUE)) {
            if (javaClass.name == service.service.className) {
                if (service.foreground) {
                    return true
                }
            }
        }
        return false
    }
}