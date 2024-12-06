package ru.lobotino.walktraveller.repositories.interfaces

import android.net.Uri

interface IScreenNavigation {
    fun navigateTo(appScreen: AppScreen, extraData: Uri? = null)
    fun openNavigationMenu()
}

enum class AppScreen {
    WELCOME_SCREEN, MAP_SCREEN, SETTINGS, RATE_THE_APP
}
