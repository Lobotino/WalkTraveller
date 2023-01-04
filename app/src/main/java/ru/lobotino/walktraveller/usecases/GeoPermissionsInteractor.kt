package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.repositories.interfaces.IPermissionsRepository
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsInteractor

class GeoPermissionsInteractor(private val permissionsRepository: IPermissionsRepository) :
    IPermissionsInteractor {

    override fun requestPermissions(
        allGranted: (() -> Unit)?,
        someDenied: ((List<String>) -> Unit)?
    ) {
        permissionsRepository.requestPermissions(allGranted, someDenied)
    }

    override fun isPermissionsGranted(): Boolean {
        return permissionsRepository.isPermissionsGranted()
    }
}