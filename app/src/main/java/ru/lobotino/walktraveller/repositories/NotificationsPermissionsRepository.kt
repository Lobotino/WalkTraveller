package ru.lobotino.walktraveller.repositories

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import ru.lobotino.walktraveller.repositories.interfaces.IPermissionsRepository

class NotificationsPermissionsRepository(fragment: Fragment, private val appContext: Context) :
    IPermissionsRepository {

    private var allGrantedCallback: (() -> Unit)? = null
    private var someDeniedCallback: ((List<String>) -> Unit)? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val notificationsPermissionsRepository = PermissionsRepository(fragment,
        arrayListOf(
            Manifest.permission.POST_NOTIFICATIONS
        ), {
            allGrantedCallback?.invoke()
        }, { deniedPermissions ->
            someDeniedCallback?.invoke(deniedPermissions)
        })

    override fun requestPermissions(
        allGranted: (() -> Unit)?,
        someDenied: ((List<String>) -> Unit)?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            allGrantedCallback = allGranted
            someDeniedCallback = someDenied
            notificationsPermissionsRepository.requestPermissions()
        } else {
            allGranted?.invoke()
        }
    }

    override fun isPermissionsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}