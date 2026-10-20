package com.jawlinefit.app.data

import android.content.Context
import com.jawlinefit.app.data.local.ProgressDao
import com.jawlinefit.app.data.local.ProgressEntry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ProgressRepository(private val context: Context, private val dao: ProgressDao) {
    val entries: Flow<List<ProgressEntry>> = dao.observeAll()

    fun newPrivatePhotoFile(): File {
        val directory = File(context.filesDir, "progress").apply { mkdirs() }
        return File(directory, "${UUID.randomUUID()}.jpg")
    }

    suspend fun save(photo: File, symmetry: Double, jawDefinition: Double, note: String) {
        require(photo.canonicalPath.startsWith(context.filesDir.canonicalPath))
        dao.upsert(ProgressEntry(UUID.randomUUID().toString(), photo.absolutePath, System.currentTimeMillis(), symmetry, jawDefinition, note.trim()))
    }

    suspend fun delete(entry: ProgressEntry) {
        File(entry.photoPath).takeIf { it.canonicalPath.startsWith(context.filesDir.canonicalPath) }?.delete()
        dao.delete(entry)
    }

    suspend fun deleteAll() {
        File(context.filesDir, "progress").listFiles()?.forEach(File::delete)
        dao.deleteAll()
    }
}
