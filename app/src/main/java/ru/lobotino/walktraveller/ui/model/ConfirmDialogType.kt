package ru.lobotino.walktraveller.ui.model

sealed interface ConfirmDialogType {
    class DeletePath(val pathId: Long) : ConfirmDialogType

    class DeleteMultiplePaths(val pathsIds: List<Long>) : ConfirmDialogType

    object GeoLocationPermissionRequired : ConfirmDialogType

    object VolumeButtonsFeatureRequest : ConfirmDialogType

    object VolumeButtonsFeatureInfo : ConfirmDialogType
}
