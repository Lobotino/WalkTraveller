package ru.lobotino.walktraveller.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.model.TileSourceType
import ru.lobotino.walktraveller.repositories.interfaces.IOptimizePathsSettingsRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPermissionsRepository
import ru.lobotino.walktraveller.repositories.permissions.GeoPermissionsRepository
import ru.lobotino.walktraveller.ui.model.SettingsUiState
import ru.lobotino.walktraveller.usecases.interfaces.ITileSourceInteractor
import ru.lobotino.walktraveller.utils.ResourceManager

class SettingsViewModel(
    private val optimizePathsSettingsRepository: IOptimizePathsSettingsRepository,
    private val tileSourceInteractor: ITileSourceInteractor,
    private val geoPermissionsRepository: GeoPermissionsRepository,
    private val notificationPermissionsRepository: IPermissionsRepository,
    private val resourceManager: ResourceManager,
) : ViewModel() {

    private val notificationChannel = Channel<String>()
    val observeNotifications: Flow<String> = notificationChannel.receiveAsFlow()

    private val settingsUiState =
        MutableStateFlow(
            SettingsUiState(
                optimizePathsSettingsRepository.getOptimizePathsApproximationDistance() ?: 1f,
                tileSourceInteractor.getCurrentTileSourceType()
            )
        )

    val observeSettingsUiState: Flow<SettingsUiState> = settingsUiState

    fun onOptimizePathsSettingsChange(newValue: Float) {
        optimizePathsSettingsRepository.setOptimizePathsApproximationDistance(newValue)
    }

    fun onMapStyleChosen(tileSourceType: TileSourceType) {
        tileSourceInteractor.setCurrentTileSourceType(tileSourceType)
    }

    fun onCheckNotificationSettingsClick() {
        if (!notificationPermissionsRepository.isPermissionsGranted()) {
            notificationPermissionsRepository.requestPermissions(
                allGranted = {
                    notificationChannel.trySend(resourceManager.getString(R.string.permission_granted))
                },
                someDenied = {
                    notificationChannel.trySend(resourceManager.getString(R.string.error_notifications_permissions_denied))
                }
            )
        } else {
            notificationChannel.trySend(resourceManager.getString(R.string.permission_granted))
        }
    }

    fun onCheckGeolocationSettingsClick() {
        if (!geoPermissionsRepository.isGeoPermissionsGranted()) {
            geoPermissionsRepository.requestPermissions(
                allGranted = {
                    notificationChannel.trySend(resourceManager.getString(R.string.permission_granted))
                },
                someDenied = {
                    notificationChannel.trySend(resourceManager.getString(R.string.error_permissions_denied))
                }
            )
        } else {
            notificationChannel.trySend(resourceManager.getString(R.string.permission_granted))
        }
    }
}
