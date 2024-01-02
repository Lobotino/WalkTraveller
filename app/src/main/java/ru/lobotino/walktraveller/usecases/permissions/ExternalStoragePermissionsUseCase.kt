package ru.lobotino.walktraveller.usecases.permissions

import ru.lobotino.walktraveller.repositories.interfaces.IPermissionsRepository
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase

class ExternalStoragePermissionsUseCase(private val externalStoragePermissionsRepository: IPermissionsRepository) : IPermissionsUseCase {
    override fun requestPermissions(allGranted: (() -> Unit)?, someDenied: ((List<String>) -> Unit)?) {
        externalStoragePermissionsRepository.requestPermissions(allGranted, someDenied)
    }

    override fun isPermissionsGranted(): Boolean {
        return externalStoragePermissionsRepository.isPermissionsGranted()
    }
}
