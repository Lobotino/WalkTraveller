package ru.lobotino.walktraveller.repositories

import android.content.SharedPreferences
import android.util.Log
import ru.lobotino.walktraveller.repositories.interfaces.IWritingPathStatesRepository

class WritingPathStatesRepository(private val sharedPreferences: SharedPreferences) :
    IWritingPathStatesRepository {

    companion object {
        private const val KEY_WRITING_PATH_NOW = "is_writing_path_now"
    }

    override fun isWritingPathNow(): Boolean {
        return sharedPreferences.getBoolean(KEY_WRITING_PATH_NOW, false)
    }

    override fun setWritingPathNow(writingPathNow: Boolean) {
        sharedPreferences
            .edit()
            .putBoolean(KEY_WRITING_PATH_NOW, writingPathNow)
            .apply()
    }
}