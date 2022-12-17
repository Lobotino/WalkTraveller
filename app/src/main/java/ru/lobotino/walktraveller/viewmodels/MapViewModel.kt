package ru.lobotino.walktraveller.viewmodels

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.lobotino.walktraveller.usecases.ILocationMediator
import ru.lobotino.walktraveller.usecases.IPermissionsInteractor

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private var permissionsInteractor: IPermissionsInteractor? = null
    private var locationMediator: ILocationMediator? = null

    private val permissionsDeniedSharedFlow =
        MutableSharedFlow<List<String>>(1, 0, BufferOverflow.DROP_OLDEST)
    val observePermissionsDeniedResult: Flow<List<String>> = permissionsDeniedSharedFlow

    private val geoLocationUpdateStateFlow = MutableStateFlow(false)
    val observeGeoLocationUpdateState: Flow<Boolean> = geoLocationUpdateStateFlow

    private val geoLocationUpdate =
        MutableSharedFlow<Pair<Double, Double>>(1, 0, BufferOverflow.DROP_OLDEST)
    val observeLocationUpdate: Flow<Pair<Double, Double>> = geoLocationUpdate

    fun setPermissionsInteractor(permissionsInteractor: IPermissionsInteractor?) {
        this.permissionsInteractor = permissionsInteractor
    }

    fun onInitFinish() {
        permissionsInteractor?.requestGeoPermissions(someDenied = { deniedPermissions ->
            permissionsDeniedSharedFlow.tryEmit(deniedPermissions)
        })
    }

    fun onGeoLocationUpdaterConnected() {
        permissionsInteractor?.requestGeoPermissions(allGranted = {
            geoLocationUpdateStateFlow.tryEmit(true)
        }, someDenied = { deniedPermissions ->
            permissionsDeniedSharedFlow.tryEmit(deniedPermissions)
        })
    }

    fun onGeoLocationUpdaterDisconnected() {
        geoLocationUpdateStateFlow.tryEmit(false)
    }

    fun onNewLocationReceive(location: Location) {
        //TODO обработка в интеракторе
        geoLocationUpdate.tryEmit(Pair(location.latitude, location.longitude))
    }
}