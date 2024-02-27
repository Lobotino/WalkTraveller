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
import ru.lobotino.walktraveller.database.dao.PathsDao
import ru.lobotino.walktraveller.database.dao.PointsDao
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPoint
import ru.lobotino.walktraveller.model.MostCommonRating

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PathsDatabaseTests {
    private lateinit var pathsDao: PathsDao
    private lateinit var pointsDao: PointsDao
    private lateinit var db: AppDatabase

    private lateinit var firstPoint: EntityPoint
    private lateinit var secondPoint: EntityPoint

    private lateinit var firstPath: EntityPath
    private lateinit var secondPath: EntityPath

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        pointsDao = db.getPointsDao()
        pathsDao = db.getPathsDao()
        firstPoint = EntityPoint(1, 1.0, 1.0, 0)
        secondPoint = EntityPoint(2, 2.0, 2.0, 1)
        firstPath = EntityPath(1, 1, 1f, MostCommonRating.UNKNOWN.ordinal, false)
        secondPath = EntityPath(2, 2, 2f, MostCommonRating.UNKNOWN.ordinal, false)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertPathAndGetById() = runTest {
        pointsDao.insertPoints(listOf(firstPoint))

        pathsDao.insertPaths(listOf(firstPath))

        assertThat(
            pathsDao.getPathById(firstPath.id),
            equalTo(firstPath)
        )
    }

    @Test
    @Throws(Exception::class)
    fun insertPathsAndGetAll() = runTest {
        pointsDao.insertPoints(listOf(firstPoint, secondPoint))

        val insertedPaths = listOf(firstPath, secondPath)
        pathsDao.insertPaths(insertedPaths)

        assertThat(
            pathsDao.getAllPaths(),
            equalTo(insertedPaths)
        )
    }

    @Test
    @Throws(Exception::class)
    fun insertPathAndGetStartPoint() = runTest {
        pointsDao.insertPoints(listOf(firstPoint))

        val insertedPaths = listOf(firstPath)
        pathsDao.insertPaths(insertedPaths)

        assertThat(
            pathsDao.getPathStartPoint(firstPoint.id),
            equalTo(firstPoint)
        )
    }

    @Test
    @Throws(Exception::class)
    fun deletePathsByIdAndGetAll() = runTest {
        pointsDao.insertPoints(listOf(firstPoint, secondPoint))

        val insertedPaths = listOf(firstPath, secondPath)
        pathsDao.insertPaths(insertedPaths)

        pathsDao.deletePathById(firstPath.id)

        assertThat(
            pathsDao.getAllPaths(),
            equalTo(listOf(secondPath))
        )
    }

    @Test
    @Throws(Exception::class)
    fun deleteAllPathsByIdsList() = runTest {
        pointsDao.insertPoints(listOf(firstPoint, secondPoint))

        pathsDao.insertPaths(listOf(firstPath, secondPath))

        pathsDao.deletePathsByIds(listOf(firstPath.id, secondPath.id))

        assertThat(
            pathsDao.getAllPaths(),
            equalTo(emptyList())
        )
    }

    @Test
    @Throws(Exception::class)
    fun deleteOnePathByIdsList() = runTest {
        pointsDao.insertPoints(listOf(firstPoint, secondPoint))

        pathsDao.insertPaths(listOf(firstPath, secondPath))

        pathsDao.deletePathsByIds(listOf(firstPath.id))

        assertThat(
            pathsDao.getAllPaths(),
            equalTo(listOf(secondPath))
        )
    }
}