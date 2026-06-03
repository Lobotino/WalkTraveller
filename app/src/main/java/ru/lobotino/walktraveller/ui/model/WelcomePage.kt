package ru.lobotino.walktraveller.ui.model

import androidx.annotation.StringRes

/** A single page of the first-launch onboarding pager. */
sealed class WelcomePage {

    /** Tutorial slide with an animated track and explanatory text. */
    data class Tutorial(
        val mode: TrackAnimationMode,
        @StringRes val titleRes: Int,
        @StringRes val subtitleRes: Int,
    ) : WelcomePage()

    /** Final slide: privacy policy / terms consent (must be accepted to continue). */
    object Consent : WelcomePage()
}
