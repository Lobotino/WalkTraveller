package ru.lobotino.walktraveller.usecases.permissions

import ru.lobotino.walktraveller.repositories.permissions.GeoPermissionsRepository

class GeoPermissionsUseCase(private val geoPermissionsRepository: GeoPermissionsRepository) {

    fun requestPermissions(
        allGranted: (() -> Unit)?,
        someDenied: ((List<String>) -> Unit)?
    ) {
        geoPermissionsRepository.requestPermissions(allGranted, someDenied)
    }

    fun isGeneralGeoPermissionsGranted(): Boolean {
        return geoPermissionsRepository.isGeneralGeoPermissionsGranted()
    }

    fun isBackgroundGeoPermissionsGranted(): Boolean {
        return geoPermissionsRepository.isBackgroundGeoPermissionsGranted()
    }
}
