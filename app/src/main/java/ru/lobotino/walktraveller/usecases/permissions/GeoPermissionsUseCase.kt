package ru.lobotino.walktraveller.usecases.permissions

import ru.lobotino.walktraveller.repositories.permissions.GeoPermissionsRepository

class GeoPermissionsUseCase(private val geoPermissionsRepository: GeoPermissionsRepository) {

    fun requestPermissions(
        allGranted: (() -> Unit)?,
        someDenied: ((List<String>) -> Unit)?
    ) {
        geoPermissionsRepository.requestPermissions(allGranted, someDenied)
    }

    fun isGeoPermissionsGranted(): Boolean {
        return geoPermissionsRepository.isGeoPermissionsGranted()
    }
}
