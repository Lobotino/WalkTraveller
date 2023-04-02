package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.repositories.interfaces.IPermissionsRepository
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsInteractor

class VolumeKeysListenerPermissionsInteractor(private val accessibilityPermissionRepository: IPermissionsRepository) :
    IPermissionsInteractor {

    override fun requestPermissions(
        allGranted: (() -> Unit)?,
        someDenied: ((List<String>) -> Unit)?
    ) {
        accessibilityPermissionRepository.requestPermissions()
    }

    override fun isPermissionsGranted(): Boolean {
        return accessibilityPermissionRepository.isPermissionsGranted()
    }
}