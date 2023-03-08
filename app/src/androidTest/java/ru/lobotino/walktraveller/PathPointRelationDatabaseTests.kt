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
import ru.lobotino.walktraveller.database.dao.PathPointsRelationsDao
import ru.lobotino.walktraveller.database.dao.PathsDao
import ru.lobotino.walktraveller.database.dao.PointsDao
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPathPointRelation
import ru.lobotino.walktraveller.database.model.EntityPoint
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PathPointRelationDatabaseTests {
    private lateinit var pathsDao: PathsDao
    private lateinit var pathsPointsRelationsDao: PathPointsRelationsDao
    private lateinit var pointsDao: PointsDao
    private lateinit var db: AppDatabase

    private lateinit var firstPath: EntityPath
    private lateinit var secondPath: EntityPath

    private lateinit var firstPoint: EntityPoint
    private lateinit var secondPoint: EntityPoint
    private lateinit var thirdPoint: EntityPoint

    private lateinit var firstPathPointRelation: EntityPathPointRelation
    private lateinit var secondPathPointRelation: EntityPathPointRelation

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        pointsDao = db.getPointsDao()
        pathsPointsRelationsDao = db.getPathPointsRelationsDao()
        pathsDao = db.getPathsDao()
        firstPath = EntityPath(1, 1, 1f)
        secondPath = EntityPath(2, 2, 2f)
        firstPoint = EntityPoint(1, 1.0, 1.0)
        secondPoint = EntityPoint(2, 2.0, 2.0)
        thirdPoint = EntityPoint(3, 3.0, 3.0)
        firstPathPointRelation = EntityPathPointRelation(1, 1)
        secondPathPointRelation = EntityPathPointRelation(2, 2)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertNewPathPoints() = runTest {
        pathsDao.insertPaths(listOf(firstPath, secondPath))
        pointsDao.insertPoints(
            listOf(
                firstPoint,
                secondPoint,
                thirdPoint
            )
        )
        val insertedPathPoints =
            listOf(firstPathPointRelation, secondPathPointRelation)
        pathsPointsRelationsDao.insertPathPointsRelations(insertedPathPoints)
        assertThat(pathsPointsRelationsDao.getAllPathPointRelations(), equalTo(insertedPathPoints))
    }

    @Test
    @Throws(Exception::class)
    fun insertNewPathPointsAndGetPaths() = runTest {
        pathsDao.insertPaths(listOf(firstPath, secondPath))
        pointsDao.insertPoints(
            listOf(
                firstPoint,
                secondPoint,
                thirdPoint
            )
        )
        val insertedPathPoints =
            listOf(firstPathPointRelation, secondPathPointRelation)
        pathsPointsRelationsDao.insertPathPointsRelations(insertedPathPoints)
        assertThat(
            pathsPointsRelationsDao.getAllPathPointRelations(), equalTo(
                listOf(
                    firstPathPointRelation, secondPathPointRelation
                )
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun deletePointWithCascadePathDelete() = runTest {
        pathsDao.insertPaths(listOf(firstPath, secondPath))
        pointsDao.insertPoints(
            listOf(
                firstPoint,
                secondPoint,
                thirdPoint
            )
        )
        pathsPointsRelationsDao.insertPathPointsRelations(
            listOf(
                firstPathPointRelation,
                EntityPathPointRelation(1, 2),
                EntityPathPointRelation(2, 2)
            )
        )
        pointsDao.deletePointById(2)
        assertThat(
            pathsPointsRelationsDao.getAllPathPointRelations(),
            equalTo(listOf(firstPathPointRelation))
        )
    }
}