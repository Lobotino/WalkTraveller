package ru.lobotino.walktraveller.utils

import androidx.annotation.StringRes

interface IResourceManager {
    fun getString(@StringRes resId: Int): String
}