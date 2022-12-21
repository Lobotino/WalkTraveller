package ru.lobotino.walktraveller

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.lobotino.walktraveller.database.AppDatabase
import ru.lobotino.walktraveller.model.MapPoint
import ru.lobotino.walktraveller.repositories.LocalPathRepository
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class LocalPathRepositoryTests {

    private lateinit var db: AppDatabase
    private lateinit var localPathRepository: LocalPathRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        localPathRepository = LocalPathRepository(db)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun addNewPathPoints() {
        val expectedList = listOf(MapPoint(1, 1), MapPoint(2, 2))
        localPathRepository.addNewPathPoints(1, expectedList)
        assertThat(localPathRepository.getAllPathPoints(1), equalTo(expectedList))
    }
}