package ru.lobotino.walktraveller.utils.ext

import org.maplibre.android.geometry.LatLng
import ru.lobotino.walktraveller.model.map.MapPoint

fun MapPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

fun LatLng.toMapPoint(): MapPoint = MapPoint(latitude, longitude)
