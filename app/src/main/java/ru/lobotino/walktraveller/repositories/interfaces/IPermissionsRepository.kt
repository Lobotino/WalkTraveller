package ru.lobotino.walktraveller.repositories.interfaces

interface IPermissionsRepository {

    fun requestPermissions(
        allGranted: (() -> Unit)? = null,
        someDenied: ((List<String>) -> Unit)? = null
    )

    fun isPermissionsGranted(): Boolean
}
