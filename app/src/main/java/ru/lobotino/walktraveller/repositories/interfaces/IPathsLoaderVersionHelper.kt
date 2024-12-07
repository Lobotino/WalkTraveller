package ru.lobotino.walktraveller.repositories.interfaces

import android.net.Uri

interface IPathsLoaderVersionHelper {

    suspend fun getVersionForParseFile(fileUri: Uri): ShareLoaderVersion

    enum class ShareLoaderVersion {
        UNKNOWN, V1
    }
}
