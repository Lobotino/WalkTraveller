package ru.lobotino.walktraveller.repositories

import android.Manifest.permission.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class GeoPermissionsRepository(fragment: Fragment, private val appContext: Context) :
    IGeoPermissionsRepository {

    private var allGrantedCallback: (() -> Unit)? = null
    private var someDeniedCallback: ((List<String>) -> Unit)? = null

    private val commonGeoPermissionsRepository = PermissionsRepository(fragment,
        arrayListOf(
            ACCESS_FINE_LOCATION,
            ACCESS_COARSE_LOCATION
        ), {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backgroundGeoPermissionsRepository.requestPermissions()
            } else {
                allGrantedCallback?.invoke()
            }
        }, { deniedPermissions ->
            someDeniedCallback?.invoke(deniedPermissions)
        })

    @RequiresApi(Build.VERSION_CODES.Q)
    private val backgroundGeoPermissionsRepository = PermissionsRepository(fragment,
        arrayListOf(
            ACCESS_BACKGROUND_LOCATION
        ), {
            allGrantedCallback?.invoke()
        }, { deniedPermissions ->
            someDeniedCallback?.invoke(deniedPermissions)
        })

    override fun requestGeoPermissions(allGranted: (() -> Unit)?, someDenied: ((List<String>) -> Unit)?) {
        allGrantedCallback = allGranted
        someDeniedCallback = someDenied
        commonGeoPermissionsRepository.requestPermissions()
    }

    override fun geoPermissionsGranted(): Boolean {
        return checkPermissions(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) arrayListOf(
                ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_BACKGROUND_LOCATION
            ) else arrayListOf(
                ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun checkPermissions(permissionList: List<String>): Boolean {
        for (permission in permissionList) {
            if (!checkPermission(permission)) return false
        }
        return true
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}