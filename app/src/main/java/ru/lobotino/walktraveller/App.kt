package ru.lobotino.walktraveller

import android.app.Application
import android.os.StrictMode
import android.preference.PreferenceManager
import com.google.firebase.analytics.FirebaseAnalytics
import org.osmdroid.config.Configuration
import ru.lobotino.walktraveller.analytics.AnalyticsTracker
import ru.lobotino.walktraveller.analytics.DebugLogAnalyticsLogger
import ru.lobotino.walktraveller.analytics.FirebaseAnalyticsLogger
import ru.lobotino.walktraveller.analytics.IAnalyticsLogger
import ru.lobotino.walktraveller.analytics.IAnalyticsTracker

class App : Application() {

    lateinit var analyticsTracker: IAnalyticsTracker
        private set

    override fun onCreate() {
        super.onCreate()
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        analyticsTracker = AnalyticsTracker(
            buildList<IAnalyticsLogger> {
                add(FirebaseAnalyticsLogger(FirebaseAnalytics.getInstance(this@App)))
                if (BuildConfig.DEBUG) {
                    add(DebugLogAnalyticsLogger())
                }
            }
        )
    }

    companion object {
        const val SHARED_PREFS_TAG = "walk_traveller_shared_prefs"
        const val PATH_DATABASE_NAME = "walk_traveller"
    }
}
