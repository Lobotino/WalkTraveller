package ru.lobotino.walktraveller.ui.model

sealed class PathItemButtonType {
    class Show(val currentState: PathInfoItemShowButtonState) : PathItemButtonType()
    object Share : PathItemButtonType()
    object Delete : PathItemButtonType()
}
