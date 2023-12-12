package ru.lobotino.walktraveller.ui.model

sealed class PathsMenuButton {
    class SelectAll(val pathsIdsInList: List<Long>) : PathsMenuButton()
    object Back : PathsMenuButton()
    object ShowSelectedPaths : PathsMenuButton()
    object FilterPathsColor : PathsMenuButton()
    object ShareSelectedPaths : PathsMenuButton()
    object DeleteSelectedPaths : PathsMenuButton()
}