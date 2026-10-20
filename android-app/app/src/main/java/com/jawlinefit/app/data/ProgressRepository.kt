package com.jawlinefit.app.data

import android.content.Context
import com.jawlinefit.app.data.local.ProgressDao
import com.jawlinefit.app.data.local.ProgressEntry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ProgressRepository(private val context: Context, private val dao: ProgressDao) {
    val entries: Flow<List<ProgressEntry>> = dao.observeAll()
    private val progressDirectory get() = File(context.filesDir, "progress")

    fun newPrivatePhotoFile(): File {
        val directory = progressDirectory
        check((directory.isDirectory || directory.mkdirs()) && directory.canWrite()) { "Private photo storage is unavailable." }
        return File(directory, "${UUID.randomUUID()}.jpg")
    }

    suspend fun save(photo: File, symmetry: Double, jawAngleDegrees: Double, note: String): Result<Unit> {
        if (!isPrivateProgressFile(photo) || photo.length() <= 0L) {
            photo.takeIf(::isWithinProgressDirectory)?.delete()
            return Result.failure(IllegalArgumentException("Captured photo is invalid."))
        }
        return runCatching {
            dao.upsert(ProgressEntry(UUID.randomUUID().toString(), photo.absolutePath, System.currentTimeMillis(), symmetry, jawAngleDegrees, note.trim()))
        }.onFailure { photo.delete() }
    }

    suspend fun delete(entry: ProgressEntry) {
        dao.delete(entry)
        File(entry.photoPath).takeIf(::isPrivateProgressFile)?.delete()
    }

    suspend fun deleteAll() {
        dao.deleteAll()
        progressDirectory.listFiles()?.filter(::isPrivateProgressFile)?.forEach(File::delete)
    }

    private fun isWithinProgressDirectory(file: File): Boolean {
        val root = progressDirectory.canonicalFile.path.trimEnd(File.separatorChar) + File.separator
        return file.canonicalFile.path.startsWith(root)
    }

    private fun isPrivateProgressFile(file: File): Boolean = isWithinProgressDirectory(file) && file.canonicalFile.isFile
}
