package ru.lobotino.walktraveller.repositories.interfaces

import android.app.Notification

interface INotificationRepository {

    fun getNotification(): Notification

    fun getNotificationId(): Int
}
