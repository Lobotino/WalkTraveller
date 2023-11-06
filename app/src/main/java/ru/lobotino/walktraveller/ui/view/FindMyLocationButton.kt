package ru.lobotino.walktraveller.ui.view

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
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

    companion object {
        private const val LOADING_SCALE_ANIMATION_DURATION = 1000L
        private const val LOADING_SCALE_MODIFIER = 1.15f
    }

    private val findMyLocationDefaultColor: Int = ContextCompat.getColor(context, R.color.black)
    private val findMyLocationLoadingColor: Int =
        ContextCompat.getColor(context, R.color.find_my_location_loading_color)
    private val findMyLocationErrorColor: Int =
        ContextCompat.getColor(context, R.color.find_my_location_error_color)

    private val findMyLocationDefaultDrawable =
        ContextCompat.getDrawable(context, R.drawable.ic_find_my_location_default)!!
    private val findMyLocationCenterOnCurrentDrawable =
        ContextCompat.getDrawable(context, R.drawable.ic_find_my_location_center_on_current)!!
    private val findMyLocationErrorDrawable =
        ContextCompat.getDrawable(context, R.drawable.ic_find_my_location_error)!!

    private lateinit var findMyLocationAnimator: ObjectAnimator

    private lateinit var findLocationImage: ImageView

    constructor(context: Context) : super(context) {
        initView(context)
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        initView(context)
    }

    constructor(context: Context, attributeSet: AttributeSet, defStyleAttr: Int) : super(
        context,
        attributeSet,
        defStyleAttr
    ) {
        initView(context)
    }

    private fun initView(context: Context) {
        LayoutInflater.from(context).inflate(R.layout.find_my_location_button, this)
        findLocationImage = findViewById(R.id.find_my_location_button_image)

        findMyLocationAnimator = ObjectAnimator.ofPropertyValuesHolder(
            findLocationImage,
            PropertyValuesHolder.ofFloat("scaleX", LOADING_SCALE_MODIFIER),
            PropertyValuesHolder.ofFloat("scaleY", LOADING_SCALE_MODIFIER)
        ).apply {
            duration = LOADING_SCALE_ANIMATION_DURATION
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
    }

    fun updateState(newState: FindMyLocationButtonState) {
        when (newState) {
            DEFAULT -> {
                stopAnimateLoadingState()
                findLocationImage.setImageDrawable(findMyLocationDefaultDrawable)
                findLocationImage.setColorFilter(findMyLocationDefaultColor)
            }

            LOADING -> {
                stopAnimateLoadingState()
                findLocationImage.setImageDrawable(findMyLocationDefaultDrawable)
                findLocationImage.setColorFilter(findMyLocationLoadingColor)
                startAnimateLoadingState()
            }

            CENTER_ON_CURRENT_LOCATION -> {
                stopAnimateLoadingState()
                findLocationImage.setImageDrawable(findMyLocationCenterOnCurrentDrawable)
                findLocationImage.setColorFilter(findMyLocationDefaultColor)
            }

            ERROR -> {
                stopAnimateLoadingState()
                findLocationImage.setImageDrawable(findMyLocationErrorDrawable)
                findLocationImage.setColorFilter(findMyLocationErrorColor)
            }
        }
    }

    private fun startAnimateLoadingState() {
        findMyLocationAnimator.start()
    }

    private fun stopAnimateLoadingState() {
        findMyLocationAnimator.cancel()
        findLocationImage.scaleX = 1.0f
        findLocationImage.scaleY = 1.0f
    }
}