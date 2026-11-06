package app.sonora.data

import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Pista descargable de una fuente con licencia libre. */
data class RemoteTrack(
    val id: String,
    val source: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String?,
    val downloadUrl: String,
    val license: String?,
    val ext: String
)

/** Para añadir nuevas fuentes legales basta con implementar esta interfaz. */
interface LegalSource {
    val name: String
    suspend fun search(query: String): List<RemoteTrack>
}

fun licenseLabel(url: String): String? {
    if (url.isBlank()) return null
    if ("publicdomain" in url || "/zero/" in url) return "Dominio público"
    val m = Regex("licenses/([a-z\\-]+)/([0-9.]+)").find(url)
    return if (m != null) "CC ${m.groupValues[1].uppercase()} ${m.groupValues[2]}" else "Licencia libre"
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/** Jamendo: API oficial (requiere Client ID gratuito) y solo pistas con descarga permitida. */
class JamendoSource(private val clientId: String) : LegalSource {
    override val name = "Jamendo"

    override suspend fun search(query: String): List<RemoteTrack> {
        val url = "https://api.jamendo.com/v3.0/tracks/?client_id=${enc(clientId)}&format=json&limit=25" +
            "&search=${enc(query)}&audiodownload_allowed=true&imagesize=300"
        val arr = JSONObject(Net.getText(url)).optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<RemoteTrack>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val dl = o.optString("audiodownload")
            if (!o.optBoolean("audiodownload_allowed", false) || dl.isBlank()) continue
            out.add(
                RemoteTrack(
                    id = o.getString("id"),
                    source = name,
                    title = o.optString("name", "Sin título"),
                    artist = o.optString("artist_name", "Artista desconocido"),
                    album = o.optString("album_name", "Álbum desconocido"),
                    coverUrl = o.optString("image").ifBlank { null },
                    downloadUrl = dl,
                    license = licenseLabel(o.optString("license_ccurl")),
                    ext = "mp3"
                )
            )
        }
        return out
    }
}

/** Internet Archive: solo elementos con licencia Creative Commons o dominio público. */
class ArchiveSource : LegalSource {
    override val name = "Internet Archive"

    override suspend fun search(query: String): List<RemoteTrack> = coroutineScope {
        val q = "($query) AND mediatype:audio AND licenseurl:(*creativecommons* OR *publicdomain*)"
        val url = "https://archive.org/advancedsearch.php?q=${enc(q)}&fl[]=identifier&rows=8&page=1" +
            "&output=json&sort[]=downloads+desc"
        val docs = JSONObject(Net.getText(url)).optJSONObject("response")?.optJSONArray("docs")
        if (docs == null) {
            emptyList()
        } else {
            val ids = (0 until docs.length()).map { docs.getJSONObject(it).getString("identifier") }
            ids.map { id -> async { runCatching { item(id) }.getOrDefault(emptyList()) } }
                .awaitAll().flatten().take(40)
        }
    }

    private fun text(v: Any?): String? = when (v) {
        is JSONArray -> (0 until v.length()).joinToString(", ") { v.optString(it) }.ifBlank { null }
        is String -> v.ifBlank { null }
        else -> null
    }

    private suspend fun item(id: String): List<RemoteTrack> {
        val root = JSONObject(Net.getText("https://archive.org/metadata/$id"))
        val md = root.optJSONObject("metadata") ?: return emptyList()
        val lic = md.optString("licenseurl")
        if ("creativecommons" !in lic && "publicdomain" !in lic) return emptyList()
        val album = text(md.opt("title")) ?: id
        val artist = text(md.opt("creator")) ?: "Artista desconocido"
        val files = root.optJSONArray("files") ?: return emptyList()
        val label = licenseLabel(lic)

        val byExt = mutableMapOf<String, MutableList<Pair<String, RemoteTrack>>>()
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            val fmt = f.optString("format")
            val name = f.optString("name")
            val ext = when {
                fmt.contains("MP3", true) -> "mp3"
                fmt.contains("Ogg Vorbis", true) -> "ogg"
                fmt.contains("FLAC", true) -> "flac"
                else -> null
            } ?: continue
            val title = f.optString("title").ifBlank { name.substringAfterLast('/').substringBeforeLast('.') }
            val track = RemoteTrack(
                id = "$id-${name.hashCode().toUInt()}",
                source = "Internet Archive",
                title = title,
                artist = artist,
                album = album,
                coverUrl = "https://archive.org/services/img/$id",
                downloadUrl = "https://archive.org/download/$id/${Uri.encode(name, "/")}",
                license = label,
                ext = ext
            )
            byExt.getOrPut(ext) { mutableListOf() }.add(fmt to track)
        }
        val mp3 = byExt["mp3"].orEmpty()
        val chosen = if (mp3.any { it.first == "VBR MP3" }) mp3.filter { it.first == "VBR MP3" }
        else mp3.ifEmpty { byExt["ogg"] ?: byExt["flac"] ?: emptyList() }
        return chosen.map { it.second }.take(25)
    }
}
