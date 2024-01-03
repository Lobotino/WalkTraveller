package ru.lobotino.walktraveller.ui.model

sealed interface ConfirmDialogInfo {
    class DeletePath(val pathId: Long) : ConfirmDialogInfo

    class DeleteMultiplePaths(val pathsIds: List<Long>) : ConfirmDialogInfo

    object GeoLocationPermissionRequired : ConfirmDialogInfo
}
