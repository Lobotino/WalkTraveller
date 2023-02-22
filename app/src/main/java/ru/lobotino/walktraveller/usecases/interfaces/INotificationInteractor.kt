package ru.lobotino.walktraveller.usecases.interfaces

import android.app.Notification

interface INotificationInteractor {

    fun showNotification()

    fun hideNotification()

    fun isNotificationShowingNow(): Boolean

    fun getNotificationId(): Int

    fun getNotification(): Notification

}