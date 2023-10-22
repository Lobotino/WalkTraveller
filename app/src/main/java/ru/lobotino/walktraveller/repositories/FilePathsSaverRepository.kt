package ru.lobotino.walktraveller.repositories

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.lang.Exception
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathsSaverRepository
import ru.lobotino.walktraveller.utils.ext.toText


class FilePathsSaverRepository(private val applicationContext: Context) : IPathsSaverRepository {

    override suspend fun saveRatingPath(path: MapRatingPath): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = generateFileName(path)
            openFileToWrite(fileName).use { file ->
                writePathToFile(path, file)
            }
            return@withContext fileName
        } catch (exception: Exception) {
            Log.w(TAG, exception)
            return@withContext null
        }
    }

    override suspend fun saveRatingPathList(paths: List<MapRatingPath>): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = generateFileName(paths)
            openFileToWrite(fileName).use { file ->
                for (path in paths) {
                    writePathToFile(path, file)
                }
            }
            return@withContext fileName
        } catch (exception: Exception) {
            Log.w(TAG, exception)
            return@withContext null
        }
    }

    private fun openFileToWrite(name: String): Writer {
        return OutputStreamWriter(
            FileOutputStream(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name + FILE_EXTENSION)
            )
        )
    }

    private fun writePathToFile(path: MapRatingPath, file: Writer) {
        file.write("$NEW_MAP_RATING_PATH_TAG\n")
        file.write("${path.pathSegments.size}\n")
        for (segment in path.pathSegments) {
            file.write("${segment.startPoint.toText()},${segment.finishPoint.toText()};${segment.rating}\n")
        }
    }

    private fun generateFileName(path: MapRatingPath): String {
        return "Path_${path.pathSegments.size + 1}_points_${Date().time}"
    }

    private fun generateFileName(paths: List<MapRatingPath>): String {
        return "Paths_${paths.size}_count_${Date().time}"
    }

    companion object {
        private val TAG = FilePathsSaverRepository::class.java.canonicalName
        private const val FILE_EXTENSION = ".wt"
        private const val NEW_MAP_RATING_PATH_TAG = "mrp"
    }
}