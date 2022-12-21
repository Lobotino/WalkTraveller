package ru.lobotino.walktraveller.repositories.interfaces

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface ILocationUpdatesRepository {

    fun startLocationUpdates()

    fun stopLocationUpdates()

    fun updateLocationNow()

    fun observeLocationUpdates(): Flow<Location>

    fun observeLocationUpdatesErrors(): Flow<String>

}