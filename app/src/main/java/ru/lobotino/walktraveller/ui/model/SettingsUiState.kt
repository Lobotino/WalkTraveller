package ru.lobotino.walktraveller.ui.model

import ru.lobotino.walktraveller.model.TileSourceType

data class SettingsUiState(
    val optimizePathsValue: Float,
    val mapStyleValue: TileSourceType
)
