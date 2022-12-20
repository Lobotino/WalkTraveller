package ru.lobotino.walktraveller.viewmodels

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.lobotino.walktraveller.usecases.IPathInteractor
import ru.lobotino.walktraveller.usecases.IPermissionsInteractor

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private var permissionsInteractor: IPermissionsInteractor? = null
    private lateinit var pathInteractor: IPathInteractor

    private val permissionsDeniedSharedFlow =
        MutableSharedFlow<List<String>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val newPathLocationFlow =
        MutableSharedFlow<Pair<Double, Double>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val mapCenterUpdateFlow =
        MutableSharedFlow<Pair<Double, Double>>(1, 0, BufferOverflow.DROP_OLDEST)
    private val regularLocationUpdateStateFlow = MutableStateFlow(false)
    private val writePathState = MutableStateFlow(false)


    val observePermissionsDeniedResult: Flow<List<String>> = permissionsDeniedSharedFlow
    val observeNewPathLocation: Flow<Pair<Double, Double>> = newPathLocationFlow
    val observeMapCenterUpdate: Flow<Pair<Double, Double>> = mapCenterUpdateFlow
    val observeRegularLocationUpdateState: Flow<Boolean> = regularLocationUpdateStateFlow
    val observeWritePathState: Flow<Boolean> = writePathState

    fun setPermissionsInteractor(permissionsInteractor: IPermissionsInteractor) {
        this.permissionsInteractor = permissionsInteractor
    }

    fun setPathInteractor(pathInteractor: IPathInteractor) {
        this.pathInteractor = pathInteractor
    }

    fun onInitFinish() {
        permissionsInteractor?.requestGeoPermissions(someDenied = { deniedPermissions ->
            permissionsDeniedSharedFlow.tryEmit(deniedPermissions)
        })

        mapCenterUpdateFlow.tryEmit(pathInteractor.getLastPathFinishPosition())
    }

    fun onGeoLocationUpdaterConnected() {
        permissionsInteractor?.requestGeoPermissions(someDenied = { deniedPermissions ->
            permissionsDeniedSharedFlow.tryEmit(deniedPermissions)
        })
    }

    fun onGeoLocationUpdaterDisconnected() {
        regularLocationUpdateStateFlow.tryEmit(false)
    }

    fun onNewLocationReceive(location: Location) {
        newPathLocationFlow.tryEmit(Pair(location.latitude, location.longitude))
    }

    fun onStartPathButtonClicked() {
        writePathState.tryEmit(true)
        regularLocationUpdateStateFlow.tryEmit(true)
    }

    fun onStopPathButtonClicked() {
        writePathState.tryEmit(false)
        regularLocationUpdateStateFlow.tryEmit(false)
    }
}