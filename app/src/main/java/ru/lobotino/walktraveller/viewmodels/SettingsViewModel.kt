package ru.lobotino.walktraveller.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.lobotino.walktraveller.repositories.interfaces.IOptimizePathsSettingsRepository
import ru.lobotino.walktraveller.ui.model.SettingsUiState

class SettingsViewModel(private val optimizePathsSettingsRepository: IOptimizePathsSettingsRepository) :
    ViewModel() {

    private val settingsUiState =
        MutableStateFlow(
            SettingsUiState(
                optimizePathsSettingsRepository.getOptimizePathsApproximationDistance() ?: 1f
            )
        )

    val observeSettingsUiState: Flow<SettingsUiState> = settingsUiState

    fun onOptimizePathsSettingsChange(newValue: Float) {
        optimizePathsSettingsRepository.setOptimizePathsApproximationDistance(newValue)
    }
}
