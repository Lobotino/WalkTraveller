package ru.lobotino.walktraveller.repositories.permissions

import android.Manifest.permission.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class GeoPermissionsRepository(fragment: Fragment, private val appContext: Context) {

    private var allGrantedCallback: (() -> Unit)? = null
    private var someDeniedCallback: ((List<String>) -> Unit)? = null

    private val commonGeoPermissionsRepository = PermissionsRepository(
        fragment,
        arrayListOf(
            ACCESS_FINE_LOCATION,
            ACCESS_COARSE_LOCATION
        ),
        {
            allGrantedCallback?.invoke()
        },
        { deniedPermissions ->
            someDeniedCallback?.invoke(deniedPermissions)
        }
    )

    fun requestPermissions(
        allGranted: (() -> Unit)?,
        someDenied: ((List<String>) -> Unit)?,
    ) {
        allGrantedCallback = allGranted
        someDeniedCallback = someDenied
        commonGeoPermissionsRepository.requestPermissions()
    }

    fun isGeoPermissionsGranted(): Boolean {
        return checkPermission(ACCESS_FINE_LOCATION) || checkPermission(ACCESS_COARSE_LOCATION)
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
