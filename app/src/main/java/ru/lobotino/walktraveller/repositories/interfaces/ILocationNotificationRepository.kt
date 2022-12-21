package ru.lobotino.walktraveller.repositories.interfaces

import android.app.Notification
import android.location.Location

interface ILocationNotificationRepository {

    fun getLocationNotification(location: Location?): Notification

    fun getNotificationId(): Int

}