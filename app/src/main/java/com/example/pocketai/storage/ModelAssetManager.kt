package com.example.pocketai.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ModelAssetManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelAssetManager"
        private const val BUFFER_SIZE = 1024 * 64 // Highly optimized 64KB extraction chunk buffer size
    }

    /**
     * Checks for the presence of the model file inside local app storage.
     * Extracts and copies the file from compressed assets if it's missing.
     * Returns the absolute file instance ready for immediate LiteRT-LM instantiation.
     */
    suspend fun secureLocalModelInstance(modelFileName: String): File = withContext(Dispatchers.IO) {
        val targetLocalFile = File(context.filesDir, modelFileName)

        // 1. If the file is already safely uncompressed in the local workspace, skip extraction
        if (targetLocalFile.exists() && targetLocalFile.length() > 0) {
            Log.d(TAG, "Local model instance validated at: ${targetLocalFile.absolutePath} (${targetLocalFile.length()} bytes)")
            return@withContext targetLocalFile
        }

        Log.i(TAG, "First launch detected or model missing. Commencing extraction of: $modelFileName")

        // 2. Clear out any broken file frames or partial remnants from previous failed extractions
        if (targetLocalFile.exists()) {
            targetLocalFile.delete()
        }

        runCatching {
            context.assets.open(modelFileName).use { inputStream ->
                FileOutputStream(targetLocalFile).use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    // Stream chunks sequentially off the main thread to avoid stuttering the UI
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }
            }
            Log.i(TAG, "Successfully extracted and isolated model binary at: ${targetLocalFile.absolutePath}")
        }.onFailure { exception ->
            // Cleanup partial files if a crash occurs (e.g., storage full)
            if (targetLocalFile.exists()) {
                targetLocalFile.delete()
            }
            Log.e(TAG, "Critical error migrating model asset to sandboxed storage: ${exception.localizedMessage}", exception)
            throw exception
        }

        return@withContext targetLocalFile
    }

    /**
     * Checks if a local model file already exists in filesDir or if an asset bundle is packaged.
     */
    suspend fun isModelAvailable(modelFileName: String): Boolean = withContext(Dispatchers.IO) {
        val targetLocalFile = File(context.filesDir, modelFileName)
        if (targetLocalFile.exists() && targetLocalFile.length() > 0) {
            return@withContext true
        }
        return@withContext try {
            context.assets.open(modelFileName).use { true }
        } catch (_: Exception) {
            false
        }
    }
}
