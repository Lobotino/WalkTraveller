package ru.lobotino.walktraveller.utils

import android.content.Context
import android.util.DisplayMetrics

object Utils {
    fun convertDpToPixel(context: Context, dp: Float): Float {
        return dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }
}
