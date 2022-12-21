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
import ru.lobotino.walktraveller.database.dao.PathDao
import ru.lobotino.walktraveller.database.dao.PointsDao
import ru.lobotino.walktraveller.database.model.PathPointRelation
import ru.lobotino.walktraveller.database.model.Point
import ru.lobotino.walktraveller.model.MapPoint
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PathPointRelationDatabaseTests {
    private lateinit var pathsDao: PathDao
    private lateinit var pointsDao: PointsDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        pointsDao = db.getPointsDao()
        pathsDao = db.getPathsDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertNewPathPoints() {
        pointsDao.insertPoints(listOf(Point(1, 1, 1), Point(2, 2, 2), Point(3, 3, 3)))
        val insertedPathPoints = listOf(PathPointRelation(1, 1), PathPointRelation(2, 2))
        pathsDao.insertPathPoints(insertedPathPoints)
        assertThat(pathsDao.getAllPathPointRelations(), equalTo(insertedPathPoints))
    }

    @Test
    @Throws(Exception::class)
    fun insertNewPathPointsAndGetPaths() {
        pointsDao.insertPoints(listOf(Point(1, 1, 1), Point(2, 2, 2), Point(3, 3, 3)))
        val insertedPathPoints = listOf(PathPointRelation(1, 1), PathPointRelation(2, 2))
        pathsDao.insertPathPoints(insertedPathPoints)
        assertThat(pathsDao.getAllPathsIds(), equalTo(listOf(1L, 2L)))
    }

    @Test
    @Throws(Exception::class)
    fun insertNewPathPointsAndFindById() {
        pointsDao.insertPoints(listOf(Point(1, 1, 1), Point(2, 2, 2), Point(3, 3, 3)))
        val insertedPathPoints = listOf(PathPointRelation(1, 1), PathPointRelation(1, 2))
        pathsDao.insertPathPoints(insertedPathPoints)
        assertThat(pathsDao.getPathPointsById(1), equalTo(listOf(MapPoint(1, 1), MapPoint(2, 2))))
    }

    @Test
    @Throws(Exception::class)
    fun deletePathPointsRelationsById() {
        pointsDao.insertPoints(listOf(Point(1, 1, 1), Point(2, 2, 2), Point(3, 3, 3)))
        pathsDao.insertPathPoints(
            listOf(
                PathPointRelation(1, 1),
                PathPointRelation(1, 2),
                PathPointRelation(2, 2)
            )
        )
        pathsDao.deletePathById(1)
        assertThat(pathsDao.getAllPathPointRelations(), equalTo(listOf(PathPointRelation(2, 2))))
    }

    @Test
    @Throws(Exception::class)
    fun deleteAllPathPointsRelations() {
        pointsDao.insertPoints(listOf(Point(1, 1, 1), Point(2, 2, 2), Point(3, 3, 3)))
        pathsDao.insertPathPoints(
            listOf(
                PathPointRelation(1, 1),
                PathPointRelation(1, 2),
                PathPointRelation(2, 2)
            )
        )
        pathsDao.deleteAllPathPoints()
        assertThat(pathsDao.getAllPathPointRelations(), equalTo(emptyList()))
    }


    @Test
    @Throws(Exception::class)
    fun deletePointWithCascadePathDelete() {
        pointsDao.insertPoints(listOf(Point(1, 1, 1), Point(2, 2, 2), Point(3, 3, 3)))
        pathsDao.insertPathPoints(
            listOf(
                PathPointRelation(1, 1),
                PathPointRelation(1, 2),
                PathPointRelation(2, 2)
            )
        )
        pointsDao.deletePointById(2)
        assertThat(pathsDao.getAllPathPointRelations(), equalTo(listOf(PathPointRelation(1, 1))))
    }
}