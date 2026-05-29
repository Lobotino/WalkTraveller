package ru.lobotino.walktraveller.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

class FirebaseAnalyticsLogger(
    private val firebaseAnalytics: FirebaseAnalytics,
) : IAnalyticsLogger {

    override fun log(event: AnalyticsEvent) {
        val payload = event.toFirebasePayload()
        firebaseAnalytics.logEvent(payload.name, payload.params.toBundle())
    }

    private fun Map<String, Any>.toBundle(): Bundle {
        val bundle = Bundle()
        for ((key, value) in this) {
            when (value) {
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is String -> bundle.putString(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        return bundle
    }
}
