package ru.lobotino.walktraveller.repositories

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IPathsLoaderRepository
import ru.lobotino.walktraveller.utils.SHARE_FILE_NEW_MAP_RATING_PATH_TAG

class PathsLoaderRepository(private val applicationContext: Context) : IPathsLoaderRepository {

    override suspend fun loadAllRatingPathsFromFile(fileUri: Uri): List<List<MapPathSegment>> {
        val resultPathsList = ArrayList<MutableList<MapPathSegment>>()
        openFileToRead(fileUri).forEachLine { line ->
            if (line == SHARE_FILE_NEW_MAP_RATING_PATH_TAG) {
                resultPathsList.add(ArrayList())
            } else {
                resultPathsList.last().add(getPathSegmentFromLine(line))
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
}