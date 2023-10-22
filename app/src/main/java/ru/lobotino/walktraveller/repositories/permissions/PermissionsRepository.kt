package ru.lobotino.walktraveller.repositories.permissions

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

class PermissionsRepository(
    fragment: Fragment,
    private val permissionsList: List<String>,
    private val allGrantedCallback: (() -> Unit)?,
    private val someDeniedCallback: ((List<String>) -> Unit)?
) {
    private val permissionsRequest: ActivityResultLauncher<Array<String>> =
        fragment.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { resultPermissions ->
            val deniedPermissions = ArrayList<String>()
            for (permission in resultPermissions.entries) {
                if (!permission.value) {
                    deniedPermissions.add(permission.key)
                }
            }

            if (deniedPermissions.isEmpty()) {
                allGrantedCallback?.invoke()
            } else {
                someDeniedCallback?.invoke(deniedPermissions)
            }
        }

    fun requestPermissions() {
        permissionsRequest.launch(permissionsList.toTypedArray())
    }
}