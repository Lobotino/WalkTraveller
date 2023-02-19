package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.repositories.NotificationsPermissionsRepository
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsInteractor

class NotificationsPermissionsInteractor(private val notificationsPermissionsRepository: NotificationsPermissionsRepository) :
    IPermissionsInteractor {

    override fun requestPermissions(
        allGranted: (() -> Unit)?,
        someDenied: ((List<String>) -> Unit)?
    ) {
        notificationsPermissionsRepository.requestPermissions(allGranted, someDenied)
    }

    override fun isPermissionsGranted(): Boolean {
        return notificationsPermissionsRepository.isPermissionsGranted()
    }
}