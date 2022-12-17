package ru.lobotino.walktraveller

import android.app.Application
import android.os.StrictMode
import android.preference.PreferenceManager
import org.osmdroid.config.Configuration

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
    }
}