package ru.lobotino.walktraveller

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.lobotino.walktraveller.database.AppDatabase
import ru.lobotino.walktraveller.database.dao.PointsDao
import ru.lobotino.walktraveller.database.model.EntityPoint

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PointsDatabaseTests {
    private lateinit var pointsDao: PointsDao
    private lateinit var db: AppDatabase

    private lateinit var firstPoint: EntityPoint
    private lateinit var secondPoint: EntityPoint
    private lateinit var thirdPoint: EntityPoint

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        pointsDao = db.getPointsDao()
        firstPoint = EntityPoint(1, 1.0, 1.0, 0)
        secondPoint = EntityPoint(2, 2.0, 2.0, 1)
        thirdPoint = EntityPoint(3, 3.0, 3.0, 2)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writePointsAndReadInList() = runTest {
        val insertedPoints =
            listOf(
                firstPoint,
                secondPoint,
                thirdPoint
            )
        pointsDao.insertPoints(insertedPoints)
        val actualPointsList = pointsDao.getAllPoints()

        assertThat(
            actualPointsList,
            equalTo(
                listOf(
                    firstPoint,
                    secondPoint,
                    thirdPoint
                )
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun deletePointById() = runTest {
        pointsDao.insertPoints(listOf(firstPoint))
        assertThat(pointsDao.getAllPoints(), equalTo(listOf(firstPoint)))

        pointsDao.deletePointById(firstPoint.id)
        assertThat(pointsDao.getAllPoints(), equalTo(emptyList()))
    }
}