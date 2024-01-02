package ru.lobotino.walktraveller.utils.ext

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.MostCommonRating.BADLY
import ru.lobotino.walktraveller.model.MostCommonRating.GOOD
import ru.lobotino.walktraveller.model.MostCommonRating.NONE
import ru.lobotino.walktraveller.model.MostCommonRating.NORMAL
import ru.lobotino.walktraveller.model.MostCommonRating.PERFECT
import ru.lobotino.walktraveller.model.MostCommonRating.UNKNOWN

@ColorInt
fun MostCommonRating.toColorInt(context: Context): Int {
    return when (this) {
        BADLY -> ContextCompat.getColor(context, R.color.rating_badly)
        NORMAL -> ContextCompat.getColor(context, R.color.rating_normal)
        GOOD -> ContextCompat.getColor(context, R.color.rating_good)
        PERFECT -> ContextCompat.getColor(context, R.color.rating_perfect)
        NONE -> ContextCompat.getColor(context, R.color.rating_none)
        UNKNOWN -> ContextCompat.getColor(context, R.color.rating_unknown)
    }
}
