package ru.lobotino.walktraveller.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import ru.lobotino.walktraveller.usecases.IPermissionsInteractor

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private var permissionsInteractor: IPermissionsInteractor? = null

    private val permissionsDeniedSharedFlow = MutableSharedFlow<List<String>>(1, 0, BufferOverflow.DROP_OLDEST)
    val observePermissionsDeniedResult: Flow<List<String>> = permissionsDeniedSharedFlow

    fun setPermissionsInteractor(permissionsInteractor: IPermissionsInteractor?) {
        this.permissionsInteractor = permissionsInteractor
    }

    fun onInitFinish() {
        permissionsInteractor?.requestGeoPermissions(someDenied = { deniedPermissions ->
            permissionsDeniedSharedFlow.tryEmit(deniedPermissions)
        })
    }
}