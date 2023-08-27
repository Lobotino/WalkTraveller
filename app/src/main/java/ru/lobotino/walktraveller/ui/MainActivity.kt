package ru.lobotino.walktraveller.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.BuildConfig
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.repositories.UserInfoRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository


class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    private lateinit var navigationView: NavigationView
    private lateinit var navigationTitleVersion: TextView

    private lateinit var userInfoRepository: IUserInfoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupNavigationMenu()

        userInfoRepository = UserInfoRepository(
            getSharedPreferences(
                App.SHARED_PREFS_TAG,
                MODE_PRIVATE
            )
        )

        if (userInfoRepository.isWelcomeTutorialFinished()) {
            showMapFragment()
        } else {
            showFirstWelcomeFragment()
        }
    }

    private fun setupNavigationMenu() {
        drawerLayout = findViewById(R.id.root_drawer_layout)
        actionBarDrawerToggle =
            ActionBarDrawerToggle(this, drawerLayout, R.string.nav_open, R.string.nav_close)

        drawerLayout.addDrawerListener(actionBarDrawerToggle)

        navigationView = findViewById(R.id.navigation_view)

        navigationTitleVersion =
            navigationView.getHeaderView(0).findViewById<TextView>(R.id.navigation_title_version)
                .apply {
                    text = String.format(
                        getString(R.string.nav_version_title),
                        BuildConfig.VERSION_NAME
                    )
                }

        actionBarDrawerToggle.syncState()
    }

    fun showMapFragment() {
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, MainMapFragment())
            .commit()
    }

    private fun showFirstWelcomeFragment() {
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, FirstWelcomeFragment())
            .commit()
    }

    fun openNavigationMenu() {
        drawerLayout.open()
    }
}
