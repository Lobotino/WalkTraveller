package ru.lobotino.walktraveller

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPoint
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.repositories.DatabasePathRepository
import ru.lobotino.walktraveller.repositories.LastCreatedPathIdRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.utils.ext.toMapPoint
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DatabasePathRepositoryTests {

    private lateinit var db: AppDatabase
    private lateinit var databasePathRepository: IPathRepository

    private lateinit var pathsDao: PathsDao
    private lateinit var pointsDao: PointsDao
    private lateinit var pathPointsRelationsDao: PathPointsRelationsDao
    private lateinit var pathSegmentRelationsDao: PathSegmentRelationsDao

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
        databasePathRepository = DatabasePathRepository(
            db, LastCreatedPathIdRepository(
                context.getSharedPreferences(
                    "test_shared_prefs",
                    AppCompatActivity.MODE_PRIVATE
                )
            )
        )
        pathsDao = db.getPathsDao()
        pointsDao = db.getPointsDao()
        pathPointsRelationsDao = db.getPathPointsRelationsDao()
        pathSegmentRelationsDao = db.getPathSegmentRelationsDao()
        firstPoint = EntityPoint(1, 1.0, 1.0)
        secondPoint = EntityPoint(2, 2.0, 2.0)
        firstPath = EntityPath(1, 1)
        secondPath = EntityPath(2, 2)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun createPathWithOnePoint() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint()).let { resultPathId ->
            assertThat(
                listOf(firstPoint),
                equalTo(databasePathRepository.getAllPathPoints(resultPathId))
            )
            assertThat(
                EntityPath(resultPathId, firstPoint.id),
                equalTo(pathsDao.getPathById(resultPathId))
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun createPathWithTwoPoints() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint()).let { resultPathId ->
            databasePathRepository.addNewPathPoint(resultPathId, secondPoint.toMapPoint())

            assertThat(
                listOf(firstPoint, secondPoint),
                equalTo(pointsDao.getAllPoints())
            )
            assertThat(
                EntityPath(resultPathId, firstPoint.id),
                equalTo(pathsDao.getPathById(resultPathId))
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun createAndDeletePath() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint()).let { resultPathId ->
            databasePathRepository.addNewPathPoint(resultPathId, secondPoint.toMapPoint())

            assertThat(
                listOf(firstPoint, secondPoint),
                equalTo(databasePathRepository.getAllPathPoints(resultPathId))
            )
            assertThat(
                EntityPath(resultPathId, firstPoint.id),
                equalTo(pathsDao.getPathById(resultPathId))
            )

            databasePathRepository.deletePath(resultPathId)
        }
        assertThat(emptyList(), equalTo(pointsDao.getAllPoints()))
        assertThat(emptyList(), equalTo(pathsDao.getAllPaths()))
        assertThat(emptyList(), equalTo(pathPointsRelationsDao.getAllPathPointRelations()))
        assertThat(emptyList(), equalTo(pathSegmentRelationsDao.getAllPathSegments()))
    }

    @Test
    @Throws(Exception::class)
    fun getAllPathsInfo() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint())
        databasePathRepository.createNewPath(secondPoint.toMapPoint())
        assertThat(listOf(firstPath, secondPath), equalTo(databasePathRepository.getAllPathsInfo()))
    }

    @Test
    @Throws(Exception::class)
    fun getAllPathSegments() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint()).let { resultPathId ->
            databasePathRepository.addNewPathPoint(
                resultPathId,
                secondPoint.toMapPoint(),
                SegmentRating.NORMAL
            )

            val actualAllPathSegments = databasePathRepository.getAllPathSegments(resultPathId)

            assertThat(actualAllPathSegments.size, equalTo(1))

            val actualFirstPathSegment = actualAllPathSegments[0]

            assertThat(
                listOf(
                    actualFirstPathSegment.startPointId,
                    actualFirstPathSegment.finishPointId
                ),
                equalTo(
                    listOf(firstPoint.id, secondPoint.id)
                )
            )

            assertThat(actualFirstPathSegment.rating, equalTo(SegmentRating.NORMAL.ordinal))
        }
    }

    @Test
    @Throws(Exception::class)
    fun getPathStartSegment() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint()).let { resultPathId ->
            databasePathRepository.addNewPathPoint(
                resultPathId,
                secondPoint.toMapPoint(),
                SegmentRating.NORMAL
            )

            val actualStartPathSegment = databasePathRepository.getPathStartSegment(resultPathId)

            assertNotNull(actualStartPathSegment)

            assertThat(
                listOf(
                    actualStartPathSegment!!.startPointId,
                    actualStartPathSegment.finishPointId
                ),
                equalTo(
                    listOf(firstPoint.id, secondPoint.id)
                )
            )

            assertThat(actualStartPathSegment.rating, equalTo(SegmentRating.NORMAL.ordinal))
        }
    }

    @Test
    @Throws(Exception::class)
    fun getLastPathInfo() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint())
        assertThat(databasePathRepository.getLastPathInfo(), equalTo(firstPath))
    }

    @Test
    @Throws(Exception::class)
    fun getLastPathSegments() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint()).let { resultPathId ->
            databasePathRepository.addNewPathPoint(
                resultPathId,
                secondPoint.toMapPoint(),
                SegmentRating.NORMAL
            )

            val actualLastPathSegments = databasePathRepository.getLastPathSegments()

            assertThat(actualLastPathSegments.size, equalTo(1))

            val actualFirstPathSegment = actualLastPathSegments[0]

            assertThat(
                listOf(
                    actualFirstPathSegment.startPointId,
                    actualFirstPathSegment.finishPointId
                ),
                equalTo(
                    listOf(firstPoint.id, secondPoint.id)
                )
            )

            assertThat(actualFirstPathSegment.rating, equalTo(SegmentRating.NORMAL.ordinal))
        }
    }

    @Test
    @Throws(Exception::class)
    fun getPointInfo() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint()).let { resultPathId ->
            databasePathRepository.addNewPathPoint(
                resultPathId,
                secondPoint.toMapPoint(),
                SegmentRating.NORMAL
            )

            assertThat(firstPoint, equalTo(databasePathRepository.getPointInfo(firstPoint.id)))
            assertThat(secondPoint, equalTo(databasePathRepository.getPointInfo(secondPoint.id)))
        }
    }
}