package ru.lobotino.walktraveller.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.BuildConfig
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.repositories.UserInfoRepository
import ru.lobotino.walktraveller.repositories.interfaces.AppScreen
import ru.lobotino.walktraveller.repositories.interfaces.IScreenNavigation

class MainActivity :
    AppCompatActivity(R.layout.activity_main),
    NavigationView.OnNavigationItemSelectedListener,
    IScreenNavigation {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    private lateinit var navigationView: NavigationView
    private lateinit var navigationTitleVersion: TextView

    private lateinit var fragmentContainer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupNavigationMenu()
        checkStartNavigation()
    }

    private fun checkStartNavigation() {
        // fixme move userInfoRepository into other layer
        val userInfoRepository = UserInfoRepository(
            getSharedPreferences(
                App.SHARED_PREFS_TAG,
                MODE_PRIVATE
            )
        )

        val extraData = intent.data
        if (userInfoRepository.isWelcomeTutorialFinished()) {
            navigateTo(AppScreen.MAP_SCREEN, extraData)
        } else {
            navigateTo(AppScreen.WELCOME_SCREEN, extraData)
        }
    }

    private fun setupNavigationMenu() {
        drawerLayout = findViewById(R.id.root_drawer_layout)
        actionBarDrawerToggle =
            ActionBarDrawerToggle(this, drawerLayout, R.string.nav_open, R.string.nav_close)

        drawerLayout.addDrawerListener(actionBarDrawerToggle)

        navigationView = findViewById(R.id.navigation_view)

        fragmentContainer = findViewById(R.id.fragment_container)

        navigationTitleVersion =
            navigationView.getHeaderView(0).findViewById<TextView>(R.id.navigation_title_version)
                .apply {
                    text = String.format(
                        getString(R.string.nav_version_title),
                        BuildConfig.VERSION_NAME
                    )
                }

        actionBarDrawerToggle.syncState()

        navigationView.setNavigationItemSelectedListener(this)
    }

    private fun showMapFragment(extraData: Uri? = null) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, MainMapFragment.newInstance(extraData))
            .commit()
    }

    private fun showFirstWelcomeFragment(extraData: Uri? = null) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, FirstWelcomeFragment.newInstance(extraData))
            .commit()
    }

    private fun showSettingsFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SettingsFragment())
            .commit()
    }

    private fun navigateToRateStore() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=ru.lobotino.walktraveller")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun navigateToWriteToAuthor() {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            setData(Uri.parse("mailto:"))
            putExtra(Intent.EXTRA_EMAIL, arrayOf("walk.traveller.app@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Предложение по улучшению приложения WalkTraveller")
            putExtra(Intent.EXTRA_TEXT, "Пожелания, замечания или просто спасибо :)")
        }

        try {
            startActivity(emailIntent)
        } catch (ex: ActivityNotFoundException) {
            Snackbar.make(fragmentContainer, "Почтовый клиент не найден", Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_map -> {
                navigateTo(AppScreen.MAP_SCREEN)
            }

            R.id.nav_settings -> {
                navigateTo(AppScreen.SETTINGS)
            }

            R.id.nav_rate_app -> {
                navigateTo(AppScreen.RATE_THE_APP)
            }

            R.id.nav_write_to_author -> {
                navigateTo(AppScreen.WRITE_TO_AUTHOR)
            }
        }
        drawerLayout.close()
        return true
    }

    override fun navigateTo(appScreen: AppScreen, extraData: Uri?) {
        when (appScreen) {
            AppScreen.MAP_SCREEN -> showMapFragment(extraData)
            AppScreen.SETTINGS -> showSettingsFragment()
            AppScreen.WELCOME_SCREEN -> showFirstWelcomeFragment(extraData)
            AppScreen.RATE_THE_APP -> navigateToRateStore()
            AppScreen.WRITE_TO_AUTHOR -> navigateToWriteToAuthor()
        }
        updateNavigationMenuSelect(appScreen)
    }

    private fun updateNavigationMenuSelect(appScreen: AppScreen) {
        when (appScreen) {
            AppScreen.WELCOME_SCREEN, AppScreen.MAP_SCREEN -> navigationView.setCheckedItem(R.id.nav_map)
            AppScreen.SETTINGS -> navigationView.setCheckedItem(R.id.nav_settings)
            AppScreen.RATE_THE_APP, AppScreen.WRITE_TO_AUTHOR -> {}
        }
    }

    override fun openNavigationMenu() {
        drawerLayout.open()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val insetsController = window.insetsController
            if (insetsController != null) {
                insetsController.hide(WindowInsets.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
            }
        }
    }
}
