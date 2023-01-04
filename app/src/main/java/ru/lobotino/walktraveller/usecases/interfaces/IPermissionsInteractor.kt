package ru.lobotino.walktraveller.usecases.interfaces

interface IPermissionsInteractor {

    fun requestPermissions(
        allGranted: (() -> Unit)? = null,
        someDenied: ((List<String>) -> Unit)? = null
    )

    fun isPermissionsGranted(): Boolean

}