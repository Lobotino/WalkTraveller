package ru.lobotino.walktraveller.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.ui.model.FindMyLocationButtonState
import ru.lobotino.walktraveller.ui.model.FindMyLocationButtonState.CENTER_ON_CURRENT_LOCATION
import ru.lobotino.walktraveller.ui.model.FindMyLocationButtonState.DEFAULT
import ru.lobotino.walktraveller.ui.model.FindMyLocationButtonState.ERROR
import ru.lobotino.walktraveller.ui.model.FindMyLocationButtonState.LOADING

class FindMyLocationButton : CardView {

    private val findMyLocationDefaultColor: Int = ContextCompat.getColor(context, R.color.black)
    private val findMyLocationLoadingColor: Int =
        ContextCompat.getColor(context, R.color.user_marker_color)
    private val findMyLocationErrorColor: Int =
        ContextCompat.getColor(context, R.color.rating_badly_color)

    private val defaultStateImage: Drawable =
        ContextCompat.getDrawable(context, R.drawable.ic_find_my_location_default)!!
    private val centerStateImage: Drawable = ContextCompat.getDrawable(
        context,
        R.drawable.ic_find_my_location_center_on_current
    )!!

    private var findMyLocationImage: ImageView

    private var buttonState = DEFAULT

    constructor(context: Context) : super(context) {
        LayoutInflater.from(context).inflate(R.layout.find_my_location_button, this)
        findMyLocationImage = findViewById(R.id.my_location_button_image)
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        LayoutInflater.from(context).inflate(R.layout.find_my_location_button, this)
        findMyLocationImage = findViewById(R.id.my_location_button_image)
    }

    constructor(context: Context, attributeSet: AttributeSet, defStyleAttr: Int) : super(
        context,
        attributeSet,
        defStyleAttr
    ) {
        LayoutInflater.from(context).inflate(R.layout.find_my_location_button, this)
        findMyLocationImage = findViewById(R.id.my_location_button_image)
    }

    fun updateState(newState: FindMyLocationButtonState) {
        buttonState = newState

        when (newState) {
            DEFAULT -> {
                findMyLocationImage.setImageDrawable(defaultStateImage)
                findMyLocationImage.setColorFilter(findMyLocationDefaultColor)
            }

            LOADING -> {
                findMyLocationImage.setImageDrawable(defaultStateImage)
                findMyLocationImage.setColorFilter(findMyLocationLoadingColor)
            }

            CENTER_ON_CURRENT_LOCATION -> {
                findMyLocationImage.setImageDrawable(centerStateImage)
                findMyLocationImage.setColorFilter(findMyLocationDefaultColor)
            }

            ERROR -> {
                findMyLocationImage.setImageDrawable(defaultStateImage)
                findMyLocationImage.setColorFilter(findMyLocationErrorColor)
            }
        }
    }
}