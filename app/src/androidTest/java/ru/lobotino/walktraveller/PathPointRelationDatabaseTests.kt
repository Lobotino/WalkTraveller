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
import ru.lobotino.walktraveller.database.dao.PathPointsRelationsDao
import ru.lobotino.walktraveller.database.dao.PathsDao
import ru.lobotino.walktraveller.database.dao.PointsDao
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPathPointRelation
import ru.lobotino.walktraveller.database.model.EntityPoint
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PathPointRelationDatabaseTests {
    private lateinit var pathsDao: PathsDao
    private lateinit var pathsPointsRelationsDao: PathPointsRelationsDao
    private lateinit var pointsDao: PointsDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        pointsDao = db.getPointsDao()
        pathsPointsRelationsDao = db.getPathPointsRelationsDao()
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
        pathsDao.insertPaths(listOf(EntityPath(1, 1, "red"), EntityPath(2, 2, "blue")))
        pointsDao.insertPoints(listOf(EntityPoint(1, 1, 1), EntityPoint(2, 2, 2), EntityPoint(3, 3, 3)))
        val insertedPathPoints = listOf(EntityPathPointRelation(1, 1), EntityPathPointRelation(2, 2))
        pathsPointsRelationsDao.insertPathPointsRelations(insertedPathPoints)
        assertThat(pathsPointsRelationsDao.getAllPathPointRelations(), equalTo(insertedPathPoints))
    }

    @Test
    @Throws(Exception::class)
    fun insertNewPathPointsAndGetPaths() {
        pathsDao.insertPaths(listOf(EntityPath(1, 1, "red"), EntityPath(2, 2, "blue")))
        pointsDao.insertPoints(listOf(EntityPoint(1, 1, 1), EntityPoint(2, 2, 2), EntityPoint(3, 3, 3)))
        val insertedPathPoints = listOf(EntityPathPointRelation(1, 1), EntityPathPointRelation(2, 2))
        pathsPointsRelationsDao.insertPathPointsRelations(insertedPathPoints)
        assertThat(
            pathsPointsRelationsDao.getAllPathPointRelations(), equalTo(
                listOf(
                    EntityPathPointRelation(1, 1), EntityPathPointRelation(2, 2)
                )
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun deletePointWithCascadePathDelete() {
        pathsDao.insertPaths(listOf(EntityPath(1, 1, "red"), EntityPath(2, 2, "blue")))
        pointsDao.insertPoints(listOf(EntityPoint(1, 1, 1), EntityPoint(2, 2, 2), EntityPoint(3, 3, 3)))
        pathsPointsRelationsDao.insertPathPointsRelations(
            listOf(
                EntityPathPointRelation(1, 1),
                EntityPathPointRelation(1, 2),
                EntityPathPointRelation(2, 2)
            )
        )
        pointsDao.deletePointById(2)
        assertThat(
            pathsPointsRelationsDao.getAllPathPointRelations(),
            equalTo(listOf(EntityPathPointRelation(1, 1)))
        )
    }
}