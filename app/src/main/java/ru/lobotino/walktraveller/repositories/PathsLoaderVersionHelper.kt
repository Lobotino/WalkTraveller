package ru.lobotino.walktraveller.repositories

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.lobotino.walktraveller.repositories.interfaces.IPathsLoaderVersionHelper
import ru.lobotino.walktraveller.utils.SHARE_FILE_VERSION_1_TAG
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class PathsLoaderVersionHelper(private val applicationContext: Context) : IPathsLoaderVersionHelper {

    override suspend fun getVersionForParseFile(fileUri: Uri): IPathsLoaderVersionHelper.ShareLoaderVersion {
        return try {
            when (withContext(Dispatchers.IO) { openFileToRead(fileUri).readLine() }) {
                SHARE_FILE_VERSION_1_TAG -> IPathsLoaderVersionHelper.ShareLoaderVersion.V1
                else -> IPathsLoaderVersionHelper.ShareLoaderVersion.UNKNOWN
            }
        } catch (exception: IOException) {
            Log.w(TAG, exception)
            IPathsLoaderVersionHelper.ShareLoaderVersion.UNKNOWN
        }
    }

    private fun openFileToRead(fileUri: Uri): BufferedReader {
        val fileToReadStream = applicationContext.contentResolver.openInputStream(fileUri)
            ?: throw IOException("Error while trying to open input stream to file: $fileUri")
        return BufferedReader(InputStreamReader(fileToReadStream))
    }

    companion object {
        private val TAG = PathsLoaderVersionHelper::class.java.canonicalName
    }
}
