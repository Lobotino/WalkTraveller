package ru.lobotino.walktraveller.repositories.interfaces

interface IGeoPermissionsRepository {

    fun requestGeoPermissions(
        allGranted: (() -> Unit)? = null,
        someDenied: ((List<String>) -> Unit)? = null
    )

    fun geoPermissionsGranted(): Boolean

}