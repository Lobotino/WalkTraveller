package ru.lobotino.walktraveller.di

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import ru.lobotino.walktraveller.repositories.interfaces.IPathsSaverRepository
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IOuterPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase
import ru.lobotino.walktraveller.viewmodels.PathsMenuViewModel

class PathsMenuViewModelFactory(
    private val pathsSaverRepository: IPathsSaverRepository,
    private val externalStoragePermissionsUseCase: IPermissionsUseCase,
    private val mapPathsInteractor: IMapPathsInteractor,
    private val outerPathsInteractor: IOuterPathsInteractor,
    private val pathRedactor: IPathRedactor,
    owner: SavedStateRegistryOwner,
    bundle: Bundle?
) : AbstractSavedStateViewModelFactory(owner, bundle) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
        return when (modelClass) {
            PathsMenuViewModel::class.java -> PathsMenuViewModel(
                pathsSaverRepository,
                externalStoragePermissionsUseCase,
                mapPathsInteractor,
                outerPathsInteractor,
                pathRedactor
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
