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
import ru.lobotino.walktraveller.database.dao.PathsDao
import ru.lobotino.walktraveller.database.dao.PointsDao
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPoint
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PathsDatabaseTests {
    private lateinit var pathsDao: PathsDao
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
    fun insertPathAndGetById() {
        pointsDao.insertPoints(listOf(EntityPoint(1, 1, 1)))

        pathsDao.insertPaths(listOf(EntityPath(1, 1, "red")))

        assertThat(
            pathsDao.getPathById(1),
            equalTo(EntityPath(1, 1, "red"))
        )
    }

    @Test
    @Throws(Exception::class)
    fun insertPathsAndGetAll() {
        pointsDao.insertPoints(listOf(EntityPoint(1, 1, 1), EntityPoint(2, 2, 2)))

        val insertedPaths = listOf(EntityPath(1, 1, "red"), EntityPath(2, 2, "blue"))
        pathsDao.insertPaths(insertedPaths)

        assertThat(
            pathsDao.getAllPaths(),
            equalTo(insertedPaths)
        )
    }

    @Test
    @Throws(Exception::class)
    fun insertPathAndGetStartPoint() {
        pointsDao.insertPoints(listOf(EntityPoint(1, 1, 1)))

        val insertedPaths = listOf(EntityPath(1, 1, "red"))
        pathsDao.insertPaths(insertedPaths)

        assertThat(
            pathsDao.getPathStartPoint(1),
            equalTo(EntityPoint(1, 1, 1))
        )
    }

    @Test
    @Throws(Exception::class)
    fun deletePathsAndGetAll() {
        pointsDao.insertPoints(listOf(EntityPoint(1, 1, 1), EntityPoint(2, 2, 2)))

        val insertedPaths = listOf(EntityPath(1, 1, "red"), EntityPath(2, 2, "blue"))
        pathsDao.insertPaths(insertedPaths)

        pathsDao.deletePathById(1)

        assertThat(
            pathsDao.getAllPaths(),
            equalTo(listOf(EntityPath(2, 2, "blue")))
        )
    }
}