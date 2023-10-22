package ru.lobotino.walktraveller.repositories.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import ru.lobotino.walktraveller.repositories.interfaces.IPermissionsRepository

class ExternalStoragePermissionsRepository(fragment: Fragment, private val appContext: Context) : IPermissionsRepository {
    private var allGrantedCallback: (() -> Unit)? = null
    private var someDeniedCallback: ((List<String>) -> Unit)? = null

    private val externalStoragePermissionRepository =
        PermissionsRepository(fragment,
                              arrayListOf(
                                  Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                  Manifest.permission.READ_EXTERNAL_STORAGE
                              ), {
                                  allGrantedCallback?.invoke()
                              }, { deniedPermissions ->
                                  someDeniedCallback?.invoke(deniedPermissions)
                              })

    override fun requestPermissions(
        allGranted: (() -> Unit)?,
        someDenied: ((List<String>) -> Unit)?
    ) {
        allGrantedCallback = allGranted
        someDeniedCallback = someDenied
        externalStoragePermissionRepository.requestPermissions()
    }

    override fun isPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}