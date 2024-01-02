package ru.lobotino.walktraveller.di

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import ru.lobotino.walktraveller.repositories.interfaces.IOptimizePathsSettingsRepository
import ru.lobotino.walktraveller.viewmodels.SettingsViewModel

class SettingsViewModelFactory(
    private val optimizePathsSettingsRepository: IOptimizePathsSettingsRepository,
    owner: SavedStateRegistryOwner,
    bundle: Bundle?
) : AbstractSavedStateViewModelFactory(owner, bundle) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        return when (modelClass) {
            SettingsViewModel::class.java -> SettingsViewModel(
                optimizePathsSettingsRepository
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
