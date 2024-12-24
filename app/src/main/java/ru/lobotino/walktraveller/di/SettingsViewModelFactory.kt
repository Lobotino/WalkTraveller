package ru.lobotino.walktraveller.di

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import ru.lobotino.walktraveller.repositories.interfaces.IOptimizePathsSettingsRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPermissionsRepository
import ru.lobotino.walktraveller.repositories.permissions.GeoPermissionsRepository
import ru.lobotino.walktraveller.usecases.interfaces.ITileSourceInteractor
import ru.lobotino.walktraveller.utils.ResourceManager
import ru.lobotino.walktraveller.viewmodels.SettingsViewModel

class SettingsViewModelFactory(
    private val optimizePathsSettingsRepository: IOptimizePathsSettingsRepository,
    private val tileSourceInteractor: ITileSourceInteractor,
    private val geoPermissionsRepository: GeoPermissionsRepository,
    private val notificationPermissionsRepository: IPermissionsRepository,
    private val resourceManager: ResourceManager,
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
                optimizePathsSettingsRepository,
                tileSourceInteractor,
                geoPermissionsRepository,
                notificationPermissionsRepository,
                resourceManager
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
