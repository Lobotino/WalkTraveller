package ru.lobotino.walktraveller

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
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
import ru.lobotino.walktraveller.database.model.EntityMapPathSegment
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPathPointRelation
import ru.lobotino.walktraveller.database.model.EntityPoint
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.interop.PointWithRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.repositories.DatabasePathRepository
import ru.lobotino.walktraveller.repositories.LastCreatedPathIdRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.utils.ext.toMapPoint

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
    private lateinit var thirdPoint: EntityPoint

    private lateinit var firstPath: EntityPath
    private lateinit var secondPath: EntityPath

    private lateinit var firstPathSegment: EntityMapPathSegment
    private lateinit var secondPathSegment: EntityMapPathSegment

    @Before
    fun prepare() {
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
        firstPoint = EntityPoint(1, 1.0, 1.0, 0)
        secondPoint = EntityPoint(2, 2.0, 2.0, 1)
        thirdPoint = EntityPoint(3, 3.0, 3.0, 2)
        firstPath = EntityPath(1, 1, 0.0f, MostCommonRating.UNKNOWN.ordinal, false)
        secondPath = EntityPath(2, 2, 0.0f, MostCommonRating.UNKNOWN.ordinal, false)
        firstPathSegment = EntityMapPathSegment(
            startPoint = firstPoint,
            finishPoint = secondPoint,
            rating = SegmentRating.NORMAL.ordinal,
            timestamp = secondPoint.timestamp
        )
        secondPathSegment = EntityMapPathSegment(
            startPoint = secondPoint,
            finishPoint = thirdPoint,
            rating = SegmentRating.NORMAL.ordinal,
            timestamp = thirdPoint.timestamp
        )
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun createPathWithOnePoint() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint(), false, firstPoint.timestamp).let { resultPathId ->
            assertThat(
                databasePathRepository.getAllPathPoints(resultPathId),
                equalTo(listOf(firstPoint))
            )
            assertThat(
                pathsDao.getPathById(resultPathId),
                equalTo(
                    EntityPath(
                        resultPathId,
                        firstPoint.id,
                        firstPath.length,
                        firstPath.mostCommonRating,
                        false
                    )
                )
            )
            assertThat(pathPointsRelationsDao.getAllPathPointRelations(), equalTo(listOf(EntityPathPointRelation(resultPathId, firstPoint.id))))
        }
    }

    @Test
    @Throws(Exception::class)
    fun createPathWithTwoPoints() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint(), false, firstPoint.timestamp).let { resultPathId ->
            databasePathRepository.addNewPathPoint(resultPathId, secondPoint.toMapPoint(), timestamp = secondPoint.timestamp)

            assertThat(
                pointsDao.getAllPoints(),
                equalTo(listOf(firstPoint, secondPoint))
            )
            assertThat(
                pathsDao.getPathById(resultPathId),
                equalTo(
                    EntityPath(
                        resultPathId,
                        firstPoint.id,
                        firstPath.length,
                        firstPath.mostCommonRating,
                        false
                    )
                )
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun createAndDeletePathByStartPoint() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint(), false, firstPoint.timestamp).let { resultPathId ->
            databasePathRepository.addNewPathPoint(resultPathId, secondPoint.toMapPoint(), timestamp = secondPoint.timestamp)

            assertThat(
                databasePathRepository.getAllPathPoints(resultPathId),
                equalTo(listOf(firstPoint, secondPoint))
            )
            assertThat(
                pathsDao.getPathById(resultPathId),
                equalTo(
                    EntityPath(
                        resultPathId,
                        firstPoint.id,
                        firstPath.length,
                        firstPath.mostCommonRating,
                        false
                    )
                )
            )

            databasePathRepository.deletePath(resultPathId)
        }
        assertThat(pointsDao.getAllPoints(), equalTo(emptyList()))
        assertThat(pathsDao.getAllPaths(), equalTo(emptyList()))
        assertThat(pathPointsRelationsDao.getAllPathPointRelations(), equalTo(emptyList()))
        assertThat(pathSegmentRelationsDao.getAllMapPathSegments(), equalTo(emptyList()))
    }

    @Test
    @Throws(Exception::class)
    fun createAndDeleteOuterPathBySegments() = runTest {
        val pathId = databasePathRepository.createOuterNewPath(
            listOf(
                MapPathSegment(firstPoint.toMapPoint(), secondPoint.toMapPoint(), SegmentRating.values()[firstPathSegment.rating]),
                MapPathSegment(secondPoint.toMapPoint(), thirdPoint.toMapPoint(), SegmentRating.values()[secondPathSegment.rating])
            ),
            firstPoint.timestamp,
            1f,
            MostCommonRating.NORMAL
        )
        assertNotNull(pathId)

        pathId?.let { resultPathId ->
            assertThat(
                databasePathRepository.getAllPathPoints(resultPathId),
                equalTo(listOf(firstPoint, secondPoint, thirdPoint)),
            )

            assertThat(
                pathsDao.getPathById(resultPathId),
                equalTo(
                    EntityPath(
                        resultPathId,
                        firstPoint.id,
                        1f,
                        SegmentRating.NORMAL.ordinal,
                        true
                    )
                )
            )

            assertThat(
                pathPointsRelationsDao.getAllPathPointRelations(),
                equalTo(
                    listOf(
                        EntityPathPointRelation(resultPathId, firstPoint.id),
                        EntityPathPointRelation(resultPathId, secondPoint.id),
                        EntityPathPointRelation(resultPathId, thirdPoint.id),
                    )
                )
            )

            assertThat(
                pathSegmentRelationsDao.getAllMapPathSegments(),
                equalTo(listOf(firstPathSegment, secondPathSegment))
            )

            databasePathRepository.deletePath(resultPathId)
        }
        assertThat(pointsDao.getAllPoints(), equalTo(emptyList()))
        assertThat(pathsDao.getAllPaths(), equalTo(emptyList()))
        assertThat(pathPointsRelationsDao.getAllPathPointRelations(), equalTo(emptyList()))
        assertThat(pathSegmentRelationsDao.getAllMapPathSegments(), equalTo(emptyList()))
    }

    @Test
    @Throws(Exception::class)
    fun getAllPathsInfo() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint(), false, firstPoint.timestamp)
        databasePathRepository.createNewPath(secondPoint.toMapPoint(), false, secondPoint.timestamp)
        assertThat(databasePathRepository.getAllPathsInfo(), equalTo(listOf(firstPath, secondPath)))
    }

    @Test
    @Throws(Exception::class)
    fun getAllPathSegments() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint(), false, firstPoint.timestamp).let { resultPathId ->
            databasePathRepository.addNewPathPoint(
                resultPathId,
                secondPoint.toMapPoint(),
                SegmentRating.NORMAL,
                timestamp = secondPoint.timestamp
            )
            databasePathRepository.addNewPathPoint(
                resultPathId,
                thirdPoint.toMapPoint(),
                SegmentRating.NORMAL,
                timestamp = thirdPoint.timestamp
            )

            val actualAllPathSegments = databasePathRepository.getAllPathSegments(resultPathId)

            assertThat(actualAllPathSegments.size, equalTo(2))

            val actualFirstPathSegment = actualAllPathSegments[0]

            assertThat(
                listOf(
                    actualFirstPathSegment.startPoint,
                    actualFirstPathSegment.finishPoint
                ),
                equalTo(
                    listOf(firstPoint, secondPoint)
                )
            )

            assertThat(actualFirstPathSegment.rating, equalTo(SegmentRating.NORMAL.ordinal))

            val actualSecondPathSegment = actualAllPathSegments[1]

            assertThat(
                listOf(
                    actualSecondPathSegment.startPoint,
                    actualSecondPathSegment.finishPoint
                ),
                equalTo(
                    listOf(secondPoint, thirdPoint)
                )
            )

            assertThat(actualSecondPathSegment.rating, equalTo(SegmentRating.NORMAL.ordinal))
        }
    }

    @Test
    @Throws(Exception::class)
    fun getPathStartSegment() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint(), false, firstPoint.timestamp).let { resultPathId ->
            databasePathRepository.addNewPathPoint(
                resultPathId,
                secondPoint.toMapPoint(),
                SegmentRating.NORMAL,
                timestamp = secondPoint.timestamp
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
        databasePathRepository.createNewPath(firstPoint.toMapPoint(), false, firstPoint.timestamp)
        assertThat(databasePathRepository.getLastPathInfo(), equalTo(firstPath))
    }

    @Test
    @Throws(Exception::class)
    fun getPointInfo() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint(), false, firstPoint.timestamp).let { resultPathId ->
            databasePathRepository.addNewPathPoint(
                resultPathId,
                secondPoint.toMapPoint(),
                SegmentRating.NORMAL,
                timestamp = secondPoint.timestamp
            )

            assertThat(databasePathRepository.getPointInfo(firstPoint.id), equalTo(firstPoint))
            assertThat(databasePathRepository.getPointInfo(secondPoint.id), equalTo(secondPoint))
        }
    }

    @Test
    fun addNew3PointsCheckPointsCount() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint(), false, firstPoint.timestamp).let { resultPathId ->
            databasePathRepository.addNewPathPoints(
                resultPathId,
                listOf(
                    PointWithRating(
                        secondPoint.latitude,
                        secondPoint.longitude,
                        secondPoint.timestamp,
                        SegmentRating.NORMAL
                    ),
                    PointWithRating(
                        thirdPoint.latitude,
                        thirdPoint.longitude,
                        thirdPoint.timestamp,
                        SegmentRating.NORMAL
                    )
                )
            )

            val actualPathPoints = databasePathRepository.getAllPathPoints(resultPathId)
            assertThat(actualPathPoints.size, equalTo(3))
        }
    }

    @Test
    fun addNew3PointsCheckPoints() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint(), false, firstPoint.timestamp).let { resultPathId ->
            databasePathRepository.addNewPathPoints(
                resultPathId,
                listOf(
                    PointWithRating(
                        secondPoint.latitude,
                        secondPoint.longitude,
                        secondPoint.timestamp,
                        SegmentRating.NORMAL
                    ),
                    PointWithRating(
                        thirdPoint.latitude,
                        thirdPoint.longitude,
                        thirdPoint.timestamp,
                        SegmentRating.NORMAL
                    )
                )
            )

            val actualPathPoints = databasePathRepository.getAllPathPoints(resultPathId)
            assertThat(actualPathPoints, equalTo(listOf(firstPoint, secondPoint, thirdPoint)))
        }
    }

    @Test
    fun addNew3PointsCheckSegmentsCount() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint(), false, firstPoint.timestamp).let { resultPathId ->
            databasePathRepository.addNewPathPoints(
                resultPathId,
                listOf(
                    PointWithRating(
                        secondPoint.latitude,
                        secondPoint.longitude,
                        secondPoint.timestamp,
                        SegmentRating.NORMAL
                    ),
                    PointWithRating(
                        thirdPoint.latitude,
                        thirdPoint.longitude,
                        thirdPoint.timestamp,
                        SegmentRating.NORMAL
                    )
                )
            )

            val actualPathSegments = databasePathRepository.getAllPathSegments(resultPathId)
            assertThat(actualPathSegments.size, equalTo(2))
        }
    }

    @Test
    fun addNew3PointsCheckSegments() = runTest {
        databasePathRepository.createNewPath(firstPoint.toMapPoint(), false, firstPoint.timestamp).let { resultPathId ->
            databasePathRepository.addNewPathPoints(
                resultPathId,
                listOf(
                    PointWithRating(
                        secondPoint.latitude,
                        secondPoint.longitude,
                        secondPoint.timestamp,
                        SegmentRating.NORMAL
                    ),
                    PointWithRating(
                        thirdPoint.latitude,
                        thirdPoint.longitude,
                        thirdPoint.timestamp,
                        SegmentRating.NORMAL
                    )
                )
            )

            val actualPathSegments = databasePathRepository.getAllPathSegments(resultPathId)
            assertThat(actualPathSegments, equalTo(listOf(firstPathSegment, secondPathSegment)))
        }
    }
}