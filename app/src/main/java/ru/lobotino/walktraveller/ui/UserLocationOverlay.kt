package ru.lobotino.walktraveller.ui

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.IconOverlay
import ru.lobotino.walktraveller.R

class UserLocationOverlay(
    context: Context
) : IconOverlay() {

    init {
        mIcon = (
                AppCompatResources.getDrawable(
                    context,
                    R.drawable.ic_user_marker
                ))
    }

    fun setPosition(position: GeoPoint) {
        set(position, mIcon)
    }

    fun setRotation(rotationAngle: Float) {
        mBearing = if (rotationAngle >= 360) {
            rotationAngle % 360
        } else {
            rotationAngle
        }
    }
}