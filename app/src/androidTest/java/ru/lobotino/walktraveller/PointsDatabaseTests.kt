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
import ru.lobotino.walktraveller.database.model.Point
import ru.lobotino.walktraveller.model.MapPoint
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
    fun writePointsAndReadInList() {
        val insertedPoints =
            listOf(
                Point(latitude = 1, longitude = 1),
                Point(latitude = 2, longitude = 2),
                Point(latitude = 3, longitude = 3)
            )
        pointsDao.insertPoints(insertedPoints)
        val actualPointsList = pointsDao.getAllPoints()

        assertThat(
            actualPointsList,
            equalTo(listOf(MapPoint(1, 1), MapPoint(2, 2), MapPoint(3, 3)))
        )
    }

    @Test
    @Throws(Exception::class)
    fun deletePointById() {
        pointsDao.insertPoints(listOf(Point(1, 1, 1)))
        assertThat(pointsDao.getAllPoints(), equalTo(listOf(MapPoint(1, 1))))

        pointsDao.deletePointById(1)
        assertThat(pointsDao.getAllPoints(), equalTo(emptyList()))
    }


    @Test
    @Throws(Exception::class)
    fun deleteAllPoints() {
        val insertedPoints =
            listOf(
                Point(latitude = 1, longitude = 1),
                Point(latitude = 2, longitude = 2),
                Point(latitude = 3, longitude = 3)
            )
        pointsDao.insertPoints(insertedPoints)

        assertThat(
            pointsDao.getAllPoints(),
            equalTo(listOf(MapPoint(1, 1), MapPoint(2, 2), MapPoint(3, 3)))
        )

        pointsDao.deleteAllPoints()
        assertThat(pointsDao.getAllPoints(), equalTo(emptyList()))
    }
}