package ru.lobotino.walktraveller.repositories.interfaces

interface ILocationUpdatesStatesRepository {

    fun setRequestingLocationUpdates(requestingLocationUpdates: Boolean)

    fun isRequestingLocationUpdates(): Boolean

}