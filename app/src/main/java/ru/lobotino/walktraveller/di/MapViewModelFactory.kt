package ru.lobotino.walktraveller.di

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import ru.lobotino.walktraveller.repositories.interfaces.IPathRatingRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserRotationRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.usecases.IUserLocationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapStateInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsUseCase
import ru.lobotino.walktraveller.usecases.permissions.GeoPermissionsUseCase
import ru.lobotino.walktraveller.viewmodels.MapViewModel

class MapViewModelFactory(
    private val notificationsPermissionsInteractor: IPermissionsUseCase,
    private val volumeKeysListenerPermissionsInteractor: IPermissionsUseCase,
    private val geoPermissionsUseCase: GeoPermissionsUseCase,
    private val userLocationInteractor: IUserLocationInteractor,
    private val mapPathsInteractor: IMapPathsInteractor,
    private val mapStateInteractor: IMapStateInteractor,
    private val writingPathStatesRepository: IWritingPathStatesRepository,
    private val pathRatingRepository: IPathRatingRepository,
    private val userRotationRepository: IUserRotationRepository,
    private val userInfoRepository: IUserInfoRepository,
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
                pathRatingRepository,
                userRotationRepository,
                userInfoRepository
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
