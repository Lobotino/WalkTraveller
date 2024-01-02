package ru.lobotino.walktraveller.repositories.permissions

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Settings.Secure.ACCESSIBILITY_ENABLED
import android.util.Log
import ru.lobotino.walktraveller.repositories.interfaces.IPermissionsRepository

class AccessibilityPermissionRepository(private val appContext: Context) : IPermissionsRepository {

    companion object {
        private val TAG = AccessibilityPermissionRepository::class.java.canonicalName
    }

    override fun requestPermissions(
        allGranted: (() -> Unit)?,
        someDenied: ((List<String>) -> Unit)?
    ) {
        appContext.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    override fun isPermissionsGranted(): Boolean {
        var accessEnabled = 0
        try {
            accessEnabled =
                Settings.Secure.getInt(
                    appContext.contentResolver,
                    ACCESSIBILITY_ENABLED
                )
        } catch (e: Settings.SettingNotFoundException) {
            Log.w(TAG, "Exception on check accessibility permissions: ${e.message}")
        }
        return accessEnabled != 0
    }
}
