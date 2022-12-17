package ru.lobotino.walktraveller.usecases

import android.location.Location

interface ILocationMediator {

    fun onNewLocation(location: Location) : Pair<Double, Double>

}