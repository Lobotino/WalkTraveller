package ru.lobotino.walktraveller.repositories.interfaces

import kotlinx.coroutines.flow.Flow

interface IUserRotationRepository {

    fun startTrackUserRotation()

    fun stopTrackUserRotation()

    fun observeUserRotation(): Flow<Float>
}
