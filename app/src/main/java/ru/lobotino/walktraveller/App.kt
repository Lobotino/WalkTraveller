package ru.lobotino.walktraveller

import android.app.Application
import android.os.StrictMode
import android.preference.PreferenceManager
import org.osmdroid.config.Configuration

class App : Application() {

    companion object {
        const val SHARED_PREFS_TAG = "walk_traveller_shared_prefs"
    }

    override fun onCreate() {
        super.onCreate()
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
    }
}