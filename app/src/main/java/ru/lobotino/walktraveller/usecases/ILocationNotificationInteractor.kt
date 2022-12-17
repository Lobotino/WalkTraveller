package ru.lobotino.walktraveller.usecases

import android.app.Notification
import android.location.Location

interface ILocationNotificationInteractor {

    fun showOnLocationChangeNotification(location: Location?)

    fun getNotificationId(): Int

    fun getNotification(location: Location?): Notification

}