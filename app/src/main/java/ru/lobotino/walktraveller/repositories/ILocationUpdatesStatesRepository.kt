package ru.lobotino.walktraveller.repositories

interface ILocationUpdatesStatesRepository {

    fun setRequestingLocationUpdates(requestingLocationUpdates: Boolean)

    fun requestingLocationUpdates(): Boolean

}