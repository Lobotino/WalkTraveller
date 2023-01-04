package ru.lobotino.walktraveller.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ru.lobotino.walktraveller.R


class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, MainMapFragment())
            .commit()
    }
}
