package ru.lobotino.walktraveller.repositories

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IPathsLoaderRepository
import ru.lobotino.walktraveller.utils.SHARE_FILE_NEW_MAP_RATING_PATH_TAG
import ru.lobotino.walktraveller.utils.SHARE_FILE_VERSION_1_TAG

class PathsLoaderRepositoryV1(private val applicationContext: Context) : IPathsLoaderRepository {

    override suspend fun loadAllRatingPathsFromFile(fileUri: Uri): List<List<MapPathSegment>> {
        val resultPathsList = ArrayList<MutableList<MapPathSegment>>()
        withContext(Dispatchers.IO) {
            openFileToRead(fileUri).forEachLine { line ->
                when (line) {
                    SHARE_FILE_VERSION_1_TAG -> {
                        //skip
                    }

                    SHARE_FILE_NEW_MAP_RATING_PATH_TAG -> {
                        resultPathsList.add(ArrayList())
                    }

                    else -> {
                        try {
                            resultPathsList.last().add(getPathSegmentFromLine(line))
                        } catch (ex: Exception) {
                            Log.w(TAG, ex)
                        }
                    }
                }
            }
        }
        return resultPathsList
    }

    private fun getPathSegmentFromLine(line: String): MapPathSegment {
        val ratingDelimiterIndex = line.indexOf(";")
        val segmentsPointsString = line.substring(0, ratingDelimiterIndex)
        val pointsString = segmentsPointsString.split(",")
        val ratingString = line.substring(ratingDelimiterIndex + 1, line.length)

        val startPoint = MapPoint(pointsString[0].toDouble(), pointsString[1].toDouble())
        val finishPoint = MapPoint(pointsString[2].toDouble(), pointsString[3].toDouble())
        val rating = SegmentRating.valueOf(ratingString)

        return MapPathSegment(startPoint, finishPoint, rating)
    }

    private fun openFileToRead(fileUri: Uri): Reader {
        val fileToWriteOutputStream = applicationContext.contentResolver.openInputStream(fileUri)
            ?: throw IOException("Error while trying to open input stream to file: $fileUri")
        return InputStreamReader(fileToWriteOutputStream)
    }

    companion object {
        private val TAG = PathsLoaderRepositoryV1::class.java.canonicalName
    }
}
