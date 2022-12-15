package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.repositories.IGeoPermissionsRepository

class PermissionsInteractor(private val permissionsRepository: IGeoPermissionsRepository) :
    IPermissionsInteractor {

    override fun requestGeoPermissions(
        allGranted: (() -> Unit)?,
        someDenied: ((List<String>) -> Unit)?
    ) {
        permissionsRepository.requestGeoPermissions(allGranted, someDenied)
    }

    override fun geoPermissionsGranted(): Boolean {
        return permissionsRepository.geoPermissionsGranted()
    }
}