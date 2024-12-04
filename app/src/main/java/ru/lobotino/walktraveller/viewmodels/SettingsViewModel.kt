package ru.lobotino.walktraveller.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.lobotino.walktraveller.model.TileSourceType
import ru.lobotino.walktraveller.repositories.interfaces.IOptimizePathsSettingsRepository
import ru.lobotino.walktraveller.ui.model.SettingsUiState
import ru.lobotino.walktraveller.usecases.interfaces.ITileSourceInteractor

class SettingsViewModel(
    private val optimizePathsSettingsRepository: IOptimizePathsSettingsRepository,
    private val tileSourceInteractor: ITileSourceInteractor,
) :
    ViewModel() {

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
}
