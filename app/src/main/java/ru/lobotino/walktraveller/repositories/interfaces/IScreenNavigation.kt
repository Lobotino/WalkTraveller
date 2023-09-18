package ru.lobotino.walktraveller.repositories.interfaces

interface IScreenNavigation {
    fun navigateTo(appScreen: AppScreen)
    fun openNavigationMenu()
}

enum class AppScreen {
    WELCOME_SCREEN, MAP_SCREEN, SETTINGS
}