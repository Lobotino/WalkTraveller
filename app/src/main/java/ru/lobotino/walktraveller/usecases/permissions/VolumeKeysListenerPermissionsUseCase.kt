package ru.lobotino.walktraveller.usecases.permissions

import ru.lobotino.walktraveller.repositories.interfaces.IPermissionsRepository
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase

class VolumeKeysListenerPermissionsUseCase(private val accessibilityPermissionRepository: IPermissionsRepository) :
    IPermissionsUseCase {

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
