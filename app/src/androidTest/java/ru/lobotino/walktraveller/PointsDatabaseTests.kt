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
import ru.lobotino.walktraveller.database.dao.PointsDao
import ru.lobotino.walktraveller.database.model.EntityPoint
import ru.lobotino.walktraveller.model.map.MapPoint
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PointsDatabaseTests {
    private lateinit var pointsDao: PointsDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        pointsDao = db.getPointsDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    suspend fun writePointsAndReadInList() {
        val insertedPoints =
            listOf(
                EntityPoint(latitude = 1.0, longitude = 1.0),
                EntityPoint(latitude = 2.0, longitude = 2.0),
                EntityPoint(latitude = 3.0, longitude = 3.0)
            )
        pointsDao.insertPoints(insertedPoints)
        val actualPointsList = pointsDao.getAllPoints()

        assertThat(
            actualPointsList,
            equalTo(listOf(MapPoint(1.0, 1.0), MapPoint(2.0, 2.0), MapPoint(3.0, 3.0)))
        )
    }

    @Test
    @Throws(Exception::class)
    suspend fun deletePointById() {
        pointsDao.insertPoints(listOf(EntityPoint(1, 1.0, 1.0)))
        assertThat(pointsDao.getAllPoints(), equalTo(listOf(MapPoint(1.0, 1.0))))

        pointsDao.deletePointById(1)
        assertThat(pointsDao.getAllPoints(), equalTo(emptyList()))
    }
}