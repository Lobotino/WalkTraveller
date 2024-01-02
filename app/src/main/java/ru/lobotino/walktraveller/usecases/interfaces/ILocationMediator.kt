package ru.lobotino.walktraveller.usecases.interfaces

import android.location.Location

interface ILocationMediator {

    /**
     * Mediation for expect real location.
     *
     * It could be fake location, for example when your last location change for a 500km per second
     */
    fun onNewLocation(
        newLocation: Location,
        realLocation: ((Location) -> Unit)? = null
    )
}
