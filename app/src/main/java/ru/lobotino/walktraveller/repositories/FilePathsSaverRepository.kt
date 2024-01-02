package ru.lobotino.walktraveller.repositories

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathsSaverRepository
import ru.lobotino.walktraveller.utils.SHARE_FILE_EXTENSION
import ru.lobotino.walktraveller.utils.SHARE_FILE_NEW_MAP_RATING_PATH_TAG
import ru.lobotino.walktraveller.utils.ext.toText
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.Date

class FilePathsSaverRepository(private val applicationContext: Context) : IPathsSaverRepository {

    override suspend fun saveRatingPath(path: MapRatingPath): Uri = withContext(Dispatchers.IO) {
        val fileName = generateFileName(path)
        val fileUri = createFileToWrite(fileName) ?: throw IOException("Error while trying to open file to write")
        openFileToWrite(fileUri).use { file ->
            writePathToFile(path, file)
        }
        return@withContext fileUri
    }

    override suspend fun saveRatingPathList(paths: List<MapRatingPath>): Uri = withContext(Dispatchers.IO) {
        val fileName = generateFileName(paths)
        val fileUri = createFileToWrite(fileName) ?: throw IOException("Error while trying to open file to write")
        openFileToWrite(fileUri).use { file ->
            for (path in paths) {
                writePathToFile(path, file)
            }
        }
        return@withContext fileUri
    }

    private fun createFileToWrite(fileName: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName + SHARE_FILE_EXTENSION)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/*")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            applicationContext.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                values
            )
        } else {
            Uri.fromFile(
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName + SHARE_FILE_EXTENSION
                )
            )
        }
    }

    private fun openFileToWrite(fileUri: Uri): Writer {
        val fileToWriteOutputStream = applicationContext.contentResolver.openOutputStream(fileUri)
            ?: throw IOException("Error whilte trying to open output stream to file: $fileUri")
        return OutputStreamWriter(fileToWriteOutputStream)
    }

    private fun writePathToFile(path: MapRatingPath, file: Writer) {
        file.write("$SHARE_FILE_NEW_MAP_RATING_PATH_TAG\n")
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
    }
}
