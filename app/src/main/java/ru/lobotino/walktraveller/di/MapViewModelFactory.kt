package ru.lobotino.walktraveller.di

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserRotationRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.usecases.IUserLocationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapStateInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRatingUseCase
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase
import ru.lobotino.walktraveller.usecases.permissions.GeoPermissionsUseCase
import ru.lobotino.walktraveller.utils.IResourceManager
import ru.lobotino.walktraveller.viewmodels.MapViewModel

class MapViewModelFactory(
    private val notificationsPermissionsInteractor: IPermissionsUseCase,
    private val volumeKeysListenerPermissionsInteractor: IPermissionsUseCase,
    private val geoPermissionsUseCase: GeoPermissionsUseCase,
    private val userLocationInteractor: IUserLocationInteractor,
    private val mapPathsInteractor: IMapPathsInteractor,
    private val mapStateInteractor: IMapStateInteractor,
    private val writingPathStatesRepository: IWritingPathStatesRepository,
    private val pathRatingUseCase: IPathRatingUseCase,
    private val userRotationRepository: IUserRotationRepository,
    private val userInfoRepository: IUserInfoRepository,
    private val resourceManager: IResourceManager,
    owner: SavedStateRegistryOwner,
    bundle: Bundle?
) : AbstractSavedStateViewModelFactory(owner, bundle) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
        return when (modelClass) {
            MapViewModel::class.java -> MapViewModel(
                notificationsPermissionsInteractor,
                volumeKeysListenerPermissionsInteractor,
                geoPermissionsUseCase,
                userLocationInteractor,
                mapPathsInteractor,
                mapStateInteractor,
                writingPathStatesRepository,
                pathRatingUseCase,
                userRotationRepository,
                userInfoRepository,
                resourceManager,
                handle
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
