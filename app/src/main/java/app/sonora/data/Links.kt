package app.sonora.data

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.net.URLEncoder

data class Parsed(val service: String, val kind: String, val canonical: String, val original: String)

data class LinkInfo(
    val url: String,
    val service: String,
    val kind: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val thumbnail: String?,
    val isAudioFile: Boolean = false
)

/**
 * Reconoce enlaces de Spotify, YouTube, YouTube Music y archivos de audio directos.
 * Solo lee metadatos por vías oficiales (oEmbed y, si hay claves, la Web API de Spotify).
 * NO extrae audio de ningún servicio.
 */
object LinkResolver {
    private val audioExt = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus")
    private val spotifyKinds = setOf("track", "album", "playlist", "artist", "episode", "show")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun parse(raw: String): Parsed? {
        var text = raw.trim()
        if (text.startsWith("spotify:")) {
            val p = text.split(":")
            if (p.size >= 3) text = "https://open.spotify.com/${p[1]}/${p[2]}"
        }
        val url = Regex("https?://\\S+").find(text)?.value?.trimEnd(')', ',', '.', ';') ?: return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = (uri.host ?: return null).lowercase().removePrefix("www.")
        val segs = uri.pathSegments

        if (host == "open.spotify.com" || host == "spotify.link") {
            val i = segs.indexOfFirst { it in spotifyKinds }
            if (i >= 0 && i + 1 < segs.size) {
                val clean = "https://open.spotify.com/${segs[i]}/${segs[i + 1]}"
                return Parsed("Spotify", segs[i], clean, clean)
            }
            return Parsed("Spotify", "enlace", url, url)
        }

        val isMusic = host == "music.youtube.com"
        val isYt = host == "youtube.com" || host == "m.youtube.com" || host == "youtu.be" || host == "youtube-nocookie.com"
        if (isMusic || isYt) {
            val service = if (isMusic) "YouTube Music" else "YouTube"
            val first = segs.firstOrNull()
            val v = when {
                host == "youtu.be" -> first
                uri.getQueryParameter("v") != null -> uri.getQueryParameter("v")
                first in setOf("shorts", "embed", "live") -> segs.getOrNull(1)
                else -> null
            }
            val list = uri.getQueryParameter("list")
            val base = if (isMusic) "https://music.youtube.com" else "https://www.youtube.com"
            return when {
                v != null -> Parsed(service, if (isMusic) "canción" else "vídeo", "https://www.youtube.com/watch?v=$v", "$base/watch?v=$v")
                list != null -> Parsed(service, "lista", "https://www.youtube.com/playlist?list=$list", "$base/playlist?list=$list")
                else -> null
            }
        }

        val ext = segs.lastOrNull()?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (ext in audioExt) return Parsed("Archivo de audio", "archivo", url, url)
        return null
    }

    suspend fun resolve(p: Parsed, spotifyId: String, spotifySecret: String): LinkInfo = when (p.service) {
        "Spotify" -> resolveSpotify(p, spotifyId, spotifySecret)
        "YouTube", "YouTube Music" -> resolveYoutube(p)
        else -> {
            val name = Uri.decode(Uri.parse(p.original).lastPathSegment ?: "audio").substringBeforeLast('.')
            LinkInfo(p.original, p.service, p.kind, name, null, null, null, null, isAudioFile = true)
        }
    }

    private suspend fun resolveYoutube(p: Parsed): LinkInfo {
        val json = JSONObject(Net.getText("https://www.youtube.com/oembed?url=${enc(p.canonical)}&format=json"))
        val author = json.optString("author_name").removeSuffix(" - Topic").ifBlank { null }
        return LinkInfo(
            p.original, p.service, p.kind,
            json.optString("title", "Sin título"),
            author, null, null,
            json.optString("thumbnail_url").ifBlank { null }
        )
    }

    private var token: String? = null
    private var tokenExp = 0L

    private suspend fun spotifyToken(id: String, secret: String): String {
        val now = System.currentTimeMillis()
        token?.let { if (now < tokenExp) return it }
        val basic = Base64.encodeToString("$id:$secret".toByteArray(), Base64.NO_WRAP)
        val json = JSONObject(
            Net.postForm(
                "https://accounts.spotify.com/api/token",
                "grant_type=client_credentials",
                mapOf("Authorization" to "Basic $basic")
            )
        )
        val t = json.getString("access_token")
        token = t
        tokenExp = now + (json.optLong("expires_in", 3600) - 60) * 1000
        return t
    }

    private suspend fun resolveSpotify(p: Parsed, id: String, secret: String): LinkInfo {
        var title = ""
        var thumb: String? = null
        runCatching {
            val o = JSONObject(Net.getText("https://open.spotify.com/oembed?url=${enc(p.canonical)}"))
            title = o.optString("title")
            thumb = o.optString("thumbnail_url").ifBlank { null }
        }
        var artist: String? = null
        var album: String? = null
        var duration: Long? = null
        val m = Regex("/(track|album)/([A-Za-z0-9]+)").find(p.canonical)
        if (id.isNotBlank() && secret.isNotBlank() && m != null) {
            runCatching {
                val tok = spotifyToken(id, secret)
                val kind = m.groupValues[1]
                val o = JSONObject(
                    Net.getText(
                        "https://api.spotify.com/v1/${kind}s/${m.groupValues[2]}",
                        mapOf("Authorization" to "Bearer $tok")
                    )
                )
                title = o.optString("name", title)
                artist = o.optJSONArray("artists")?.let { a ->
                    (0 until a.length()).joinToString(", ") { a.getJSONObject(it).getString("name") }
                }
                if (kind == "track") {
                    duration = o.optLong("duration_ms").takeIf { it > 0 }
                    val alb = o.optJSONObject("album")
                    album = alb?.optString("name")
                    thumb = alb?.optJSONArray("images")?.optJSONObject(0)?.optString("url") ?: thumb
                } else {
                    album = o.optString("name")
                    thumb = o.optJSONArray("images")?.optJSONObject(0)?.optString("url") ?: thumb
                }
            }
        }
        return LinkInfo(p.original, "Spotify", p.kind, title.ifBlank { "Enlace de Spotify" }, artist, album, duration, thumb)
    }
}
