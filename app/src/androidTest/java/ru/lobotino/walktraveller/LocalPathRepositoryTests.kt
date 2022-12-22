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
import ru.lobotino.walktraveller.database.dao.PathSegmentRelationsDao
import ru.lobotino.walktraveller.database.dao.PathsDao
import ru.lobotino.walktraveller.database.dao.PointsDao
import ru.lobotino.walktraveller.database.model.Point
import ru.lobotino.walktraveller.model.MapPoint
import ru.lobotino.walktraveller.repositories.LocalPathRepository
import java.io.IOException
import java.util.concurrent.CompletableFuture

@RunWith(AndroidJUnit4::class)
class LocalPathRepositoryTests {

    private lateinit var db: AppDatabase
    private lateinit var localPathRepository: LocalPathRepository

    private lateinit var pathsDao: PathsDao
    private lateinit var pointsDao: PointsDao
    private lateinit var pathPointsRelationsDao: PathPointsRelationsDao
    private lateinit var pathSegmentRelationsDao: PathSegmentRelationsDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        localPathRepository = LocalPathRepository(db)
        pathsDao = db.getPathsDao()
        pointsDao = db.getPointsDao()
        pathPointsRelationsDao = db.getPathPointsRelationsDao()
        pathSegmentRelationsDao = db.getPathSegmentRelationsDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun createPathWithOnePoints() {
        val future: CompletableFuture<List<Point>> = CompletableFuture()
        localPathRepository.createNewPath(MapPoint(1, 1), "red") { resultPathId ->
            localPathRepository.getAllPathPoints(resultPathId) { resultPoints ->
                future.complete(resultPoints)
            }
        }
        assertThat(listOf(Point(1, 1, 1)), equalTo(future.get()))
    }

    @Test
    @Throws(Exception::class)
    fun createPathWithTwoPoints() {
        val future: CompletableFuture<List<Point>> = CompletableFuture()
        localPathRepository.createNewPath(MapPoint(1, 1), "red") { resultPathId ->
            localPathRepository.addNewPathPoint(resultPathId, MapPoint(2, 2)) {
                localPathRepository.getAllPathPoints(resultPathId) { resultPoints ->
                    future.complete(resultPoints)
                }
            }
        }
        assertThat(listOf(Point(1, 1, 1), Point(2, 2, 2)), equalTo(future.get()))
    }

    @Test
    @Throws(Exception::class)
    fun createAndDeletePath() {
        val future: CompletableFuture<List<Point>> = CompletableFuture()
        localPathRepository.createNewPath(MapPoint(1, 1), "red") { resultPathId ->
            localPathRepository.addNewPathPoint(resultPathId, MapPoint(2, 2)) {
                localPathRepository.deletePath(resultPathId) {
                    localPathRepository.getAllPathPoints(resultPathId) { resultPoints ->
                        future.complete(resultPoints)
                    }
                }
            }
        }
        assertThat(emptyList(), equalTo(future.get()))
        assertThat(emptyList(), equalTo(pointsDao.getAllPoints()))
        assertThat(emptyList(), equalTo(pathsDao.getAllPaths()))
        assertThat(emptyList(), equalTo(pathPointsRelationsDao.getAllPathPointRelations()))
        assertThat(emptyList(), equalTo(pathSegmentRelationsDao.getAllPathSegments()))
    }
}