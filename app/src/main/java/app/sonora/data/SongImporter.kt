package app.sonora.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object SongImporter {

    /** Devuelve true si la canción se añadió; false si ya existía o no se pudo leer. */
    suspend fun importOne(ctx: Context, uri: Uri, dao: SongDao): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            if (dao.count(uri.toString()) > 0) return@runCatching false

            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(ctx, uri)
                val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: return@runCatching false

                val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() } ?: displayName(ctx, uri)
                val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }
                    ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                        ?.takeIf { it.isNotBlank() }
                    ?: "Artista desconocido"
                val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() } ?: "Álbum desconocido"

                val coverPath = mmr.embeddedPicture?.let { bytes ->
                    val dir = File(ctx.filesDir, "covers").apply { mkdirs() }
                    File(dir, "${UUID.randomUUID()}.img").apply { writeBytes(bytes) }.absolutePath
                }

                dao.insert(
                    Song(
                        uri = uri.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        coverPath = coverPath
                    )
                )
                true
            } finally {
                mmr.release()
            }
        }.getOrDefault(false)
    }

    /** Recorre una carpeta (y subcarpetas) y devuelve los archivos de audio. */
    fun listAudio(ctx: Context, tree: Uri): List<Uri> {
        val out = mutableListOf<Uri>()
        fun walk(dir: DocumentFile?) {
            dir?.listFiles()?.forEach { f ->
                if (f.isDirectory) {
                    walk(f)
                } else if (f.type?.startsWith("audio/") == true || f.type == "application/ogg") {
                    out.add(f.uri)
                }
            }
        }
        walk(DocumentFile.fromTreeUri(ctx, tree))
        return out
    }

    private fun displayName(ctx: Context, uri: Uri): String =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?.substringBeforeLast('.')
            ?: "Sin título"
}
