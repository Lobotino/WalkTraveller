package ru.lobotino.walktraveller.usecases

import kotlinx.coroutines.*
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor
import ru.lobotino.walktraveller.utils.ext.toMapPoint

class LocalMapPathsInteractor(
    private val localPathRepository: IPathRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IMapPathsInteractor {

    companion object {
        private val ratingList = SegmentRating.values()
    }

    override suspend fun getAllSavedCommonPaths(): List<MapCommonPath> {
        return coroutineScope {
            ArrayList<MapCommonPath>().apply {
                for (path in withContext(defaultDispatcher) { localPathRepository.getAllPaths() }) {
                    val getStartPoint =
                        async(defaultDispatcher) { localPathRepository.getPointInfo(path.startPointId) }

                    val getAllPoints =
                        async(defaultDispatcher) { localPathRepository.getAllPathPoints(path.id) }

                    val startPoint = getStartPoint.await() ?: continue

                    val pathPoints = getAllPoints.await()
                    if (pathPoints.isEmpty()) continue

                    add(MapCommonPath(startPoint.toMapPoint(), pathPoints.map { it.toMapPoint() }))
                }
            }
        }
    }

    override suspend fun getLastSavedRatingPath(): MapRatingPath? {
        return coroutineScope {
            mapRatingPath(withContext(defaultDispatcher) { localPathRepository.getLastPathInfo() })
        }
    }

    override suspend fun getAllSavedRatingPaths(): List<MapRatingPath> {
        return coroutineScope {
            ArrayList<MapRatingPath>().apply {
                for (path in withContext(defaultDispatcher) { localPathRepository.getAllPaths() }) {
                    mapRatingPath(path)?.let { ratingPath -> add(ratingPath) }
                }
            }
        }
    }

    private suspend fun mapRatingPath(path: EntityPath?): MapRatingPath? {
        return coroutineScope {
            if (path != null) {

                val pathStartPoint =
                    withContext(defaultDispatcher) { localPathRepository.getPointInfo(path.startPointId) }
                        ?: return@coroutineScope null

                MapRatingPath(
                    pathStartPoint.toMapPoint(),
                    ArrayList<MapPathSegment>().apply {
                        for (entityPathSegment in withContext(defaultDispatcher) { localPathRepository.getLastPathSegments() }) {
                            val getStartPoint =
                                async(defaultDispatcher) {
                                    localPathRepository.getPointInfo(
                                        entityPathSegment.startPointId
                                    )
                                }
                            val getFinishPoint =
                                async(defaultDispatcher) {
                                    localPathRepository.getPointInfo(
                                        entityPathSegment.finishPointId
                                    )
                                }

                            val startPoint = getStartPoint.await()
                            val finishPoint = getFinishPoint.await()
                            if (startPoint == null || finishPoint == null) continue

                            add(
                                MapPathSegment(
                                    startPoint.toMapPoint(),
                                    finishPoint.toMapPoint(),
                                    ratingList[entityPathSegment.rating]
                                )
                            )
                        }
                    })
            } else {
                null
            }
        }
    }
}
