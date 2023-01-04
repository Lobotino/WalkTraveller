package ru.lobotino.walktraveller.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import ru.lobotino.walktraveller.R


class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, MainMapFragment())
            .commit()

        checkAccessibilityPermissionForKeyDetector()
    }

    /**
     * TODO move from main activity
     */
    private fun checkAccessibilityPermissionForKeyDetector(): Boolean {
        var accessEnabled = 0
        try {
            accessEnabled =
                Settings.Secure.getInt(this.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Settings.SettingNotFoundException) {
            Log.w("TAG", "Exception on check accessibility permissions: ${e.message}")
        }
        return if (accessEnabled == 0) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            false
        } else {
            true
        }
    }
}
