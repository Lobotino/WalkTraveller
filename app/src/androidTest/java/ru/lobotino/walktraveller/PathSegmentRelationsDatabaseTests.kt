package ru.lobotino.walktraveller

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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
import ru.lobotino.walktraveller.model.SegmentRating
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PathSegmentRelationsDatabaseTests {
    private lateinit var pathSegmentRelationsDao: PathSegmentRelationsDao
    private lateinit var pointsDao: PointsDao
    private lateinit var db: AppDatabase

    private lateinit var firstPoint: EntityPoint
    private lateinit var secondPoint: EntityPoint

    private lateinit var expectedSegment: EntityPathSegment

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        pathSegmentRelationsDao = db.getPathSegmentRelationsDao()
        pointsDao = db.getPointsDao()
        expectedSegment = EntityPathSegment(
            pathId = 1,
            startPointId = 1,
            finishPointId = 2,
            rating = SegmentRating.NORMAL.ordinal,
            timestamp = 1000
        )
        firstPoint = EntityPoint(1, 1.0, 1.0)
        secondPoint = EntityPoint(2, 2.0, 2.0)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertPathSegment() = runTest {
        val expectedPointsList = listOf(firstPoint, secondPoint)
        pointsDao.insertPoints(expectedPointsList)

        pathSegmentRelationsDao.insertPathSegments(
            listOf(expectedSegment)
        )
        assertThat(
            listOf(expectedSegment),
            equalTo(pathSegmentRelationsDao.getAllPathSegments())
        )
    }

    @Test
    @Throws(Exception::class)
    fun getNextPointWithPathSegment() = runTest {
        val expectedPointsList = listOf(firstPoint, secondPoint)
        pointsDao.insertPoints(expectedPointsList)

        pathSegmentRelationsDao.insertPathSegments(listOf(expectedSegment))
        assertThat(
            secondPoint,
            equalTo(pathSegmentRelationsDao.getNextPathPoint(firstPoint.id))
        )
    }

    @Test
    @Throws(Exception::class)
    fun getNullNextPointWithPathSegment() = runTest {
        val expectedPointsList = listOf(firstPoint, secondPoint)
        pointsDao.insertPoints(expectedPointsList)

        pathSegmentRelationsDao.insertPathSegments(listOf(expectedSegment))
        assertThat(
            null,
            equalTo(pathSegmentRelationsDao.getNextPathPoint(secondPoint.id))
        )
    }
}