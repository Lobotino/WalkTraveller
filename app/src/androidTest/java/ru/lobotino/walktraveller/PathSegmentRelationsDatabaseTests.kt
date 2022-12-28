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
import ru.lobotino.walktraveller.database.dao.PathSegmentRelationsDao
import ru.lobotino.walktraveller.database.dao.PointsDao
import ru.lobotino.walktraveller.database.model.EntityPathSegment
import ru.lobotino.walktraveller.database.model.EntityPoint
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PathSegmentRelationsDatabaseTests {
    private lateinit var pathSegmentRelationsDao: PathSegmentRelationsDao
    private lateinit var pointsDao: PointsDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        pathSegmentRelationsDao = db.getPathSegmentRelationsDao()
        pointsDao = db.getPointsDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertPathSegment() {
        val expectedPointsList = listOf(EntityPoint(1, 1, 1), EntityPoint(2, 2, 2))
        pointsDao.insertPoints(expectedPointsList)

        pathSegmentRelationsDao.insertPathSegments(listOf(EntityPathSegment(1, 2)))
        assertThat(
            listOf(EntityPathSegment(1, 2)),
            equalTo(pathSegmentRelationsDao.getAllPathSegments())
        )
    }

    @Test
    @Throws(Exception::class)
    fun getNextPointWithPathSegment() {
        val expectedPointsList = listOf(EntityPoint(1, 1, 1), EntityPoint(2, 2, 2))
        pointsDao.insertPoints(expectedPointsList)

        pathSegmentRelationsDao.insertPathSegments(listOf(EntityPathSegment(1, 2)))
        assertThat(
            EntityPoint(2, 2, 2),
            equalTo(pathSegmentRelationsDao.getNextPathPoint(1))
        )
    }

    @Test
    @Throws(Exception::class)
    fun getNullNextPointWithPathSegment() {
        val expectedPointsList = listOf(EntityPoint(1, 1, 1), EntityPoint(2, 2, 2))
        pointsDao.insertPoints(expectedPointsList)

        pathSegmentRelationsDao.insertPathSegments(listOf(EntityPathSegment(1, 2)))
        assertThat(
            null,
            equalTo(pathSegmentRelationsDao.getNextPathPoint(2))
        )
    }
}