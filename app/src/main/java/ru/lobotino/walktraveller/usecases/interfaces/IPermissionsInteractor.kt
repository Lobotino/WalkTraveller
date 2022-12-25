package ru.lobotino.walktraveller.usecases.interfaces

interface IPermissionsInteractor {

    fun requestGeoPermissions(
        allGranted: (() -> Unit)? = null,
        someDenied: ((List<String>) -> Unit)? = null
    )

    fun geoPermissionsGranted(): Boolean

}