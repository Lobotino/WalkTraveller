package ru.lobotino.walktraveller.repositories

import android.content.SharedPreferences
import ru.lobotino.walktraveller.repositories.interfaces.ILastCreatedPathIdRepository

class LastCreatedPathIdRepository(private val sharedPreferences: SharedPreferences) :
    ILastCreatedPathIdRepository {

    companion object {
        private const val KEY_LAST_PATH_ID = "last_path_id"
    }

    override fun getLastCreatedPathId(): Long? {
        val lastPathId = sharedPreferences.getLong(KEY_LAST_PATH_ID, -1L)
        return if (lastPathId == -1L) null else lastPathId
    }

    override fun setLastCreatedPathId(pathId: Long) {
        sharedPreferences.edit().apply {
            putLong(KEY_LAST_PATH_ID, pathId)
            apply()
        }
    }
}