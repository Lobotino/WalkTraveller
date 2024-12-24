package ru.lobotino.walktraveller.usecases

import android.app.Notification
import android.app.NotificationManager
import ru.lobotino.walktraveller.repositories.interfaces.INotificationRepository
import ru.lobotino.walktraveller.usecases.interfaces.INotificationInteractor

class PathWritingNowNotificationInteractor(
    private val notificationManager: NotificationManager,
    private val writingPathNotificationRepository: INotificationRepository
) : INotificationInteractor {

    private var isNotificationShowingNow = false

    override fun showNotification() {
        notificationManager.notify(
            writingPathNotificationRepository.getNotificationId(),
            writingPathNotificationRepository.getNotification()
        )
        isNotificationShowingNow = true
    }

    override fun hideNotification() {
        notificationManager.cancel(writingPathNotificationRepository.getNotificationId())
        isNotificationShowingNow = false
    }

    override fun isNotificationShowingNow(): Boolean {
        for (notification in notificationManager.activeNotifications) {
            if (notification.id == getNotificationId()) {
                isNotificationShowingNow = true
                return true
            }
        }
        return false
    }

    override fun getNotificationId(): Int {
        return writingPathNotificationRepository.getNotificationId()
    }

    override fun getNotification(): Notification {
        return writingPathNotificationRepository.getNotification()
    }
}
