package ru.lobotino.walktraveller.utils.ext

import android.net.Uri
import androidx.fragment.app.Fragment
import ru.lobotino.walktraveller.repositories.interfaces.AppScreen
import ru.lobotino.walktraveller.repositories.interfaces.IScreenNavigation

fun Fragment.openNavigationMenu() {
    val activity = activity
    if (activity != null && activity is IScreenNavigation) {
        activity.openNavigationMenu()
    }
}

fun Fragment.navigateTo(appScreen: AppScreen, extraData: Uri? = null) {
    val activity = activity
    if (activity != null && activity is IScreenNavigation) {
        activity.navigateTo(appScreen, extraData)
    }
}