package ru.lobotino.walktraveller.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ru.lobotino.walktraveller.App
import ru.lobotino.walktraveller.R
import ru.lobotino.walktraveller.repositories.UserInfoRepository
import ru.lobotino.walktraveller.repositories.interfaces.IUserInfoRepository


class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private lateinit var userInfoRepository: IUserInfoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
}
