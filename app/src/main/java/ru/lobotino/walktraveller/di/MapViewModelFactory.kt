package ru.lobotino.walktraveller.di

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import ru.lobotino.walktraveller.repositories.interfaces.IPathRatingRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserRotationRepository
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository
import ru.lobotino.walktraveller.usecases.GeoPermissionsInteractor
import ru.lobotino.walktraveller.usecases.IUserLocationInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IMapStateInteractor
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor
import ru.lobotino.walktraveller.usecases.interfaces.IPermissionsInteractor
import ru.lobotino.walktraveller.viewmodels.MapViewModel

class MapViewModelFactory(
    private val notificationsPermissionsInteractor: IPermissionsInteractor,
    private val volumeKeysListenerPermissionsInteractor: IPermissionsInteractor,
    private val geoPermissionsInteractor: GeoPermissionsInteractor,
    private val userLocationInteractor: IUserLocationInteractor,
    private val mapPathsInteractor: IMapPathsInteractor,
    private val mapStateInteractor: IMapStateInteractor,
    private val writingPathStatesRepository: IWritingPathStatesRepository,
    private val pathRatingRepository: IPathRatingRepository,
    private val userRotationRepository: IUserRotationRepository,
    private val pathRedactor: IPathRedactor,
    owner: SavedStateRegistryOwner,
    bundle: Bundle?
) : AbstractSavedStateViewModelFactory(owner, bundle) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
        return when (modelClass) {
            MapViewModel::class.java -> MapViewModel(
                notificationsPermissionsInteractor,
                volumeKeysListenerPermissionsInteractor,
                geoPermissionsInteractor,
                userLocationInteractor,
                mapPathsInteractor,
                mapStateInteractor,
                writingPathStatesRepository,
                pathRatingRepository,
                userRotationRepository,
                pathRedactor
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}