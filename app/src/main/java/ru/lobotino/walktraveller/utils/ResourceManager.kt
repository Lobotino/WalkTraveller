package ru.lobotino.walktraveller.utils

import android.content.Context

class ResourceManager(private val appContext: Context) : IResourceManager {
    override fun getString(resId: Int): String {
        return appContext.getString(resId)
    }
}