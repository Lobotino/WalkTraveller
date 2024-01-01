package ru.lobotino.walktraveller.ui.model

sealed class PathsToAction {
    object All : PathsToAction()

    class Multiple(val pathIds: List<Long>) : PathsToAction()

    class Single(val pathId: Long) : PathsToAction()
}