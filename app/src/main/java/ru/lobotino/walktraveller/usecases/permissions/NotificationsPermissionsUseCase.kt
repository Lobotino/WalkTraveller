package ru.lobotino.walktraveller.usecases.permissions

import ru.lobotino.walktraveller.repositories.permissions.NotificationsPermissionsRepository
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase

class NotificationsPermissionsUseCase(private val notificationsPermissionsRepository: NotificationsPermissionsRepository) :
    IPermissionsUseCase {

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