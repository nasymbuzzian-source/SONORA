package app.sonora.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Acceso HTTP mínimo (sin dependencias). Solo HTTPS en la práctica. */
object Net {
    private const val UA = "SONORA/0.3 (app personal de musica)"

    private fun open(url: String, method: String = "GET", headers: Map<String, String> = emptyMap()): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 12_000
        c.readTimeout = 25_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        return c
    }

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val c = open(url, "GET", headers)
            try {
                if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }

    suspend fun postForm(url: String, body: String, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val c = open(url, "POST", headers + ("Content-Type" to "application/x-www-form-urlencoded"))
            try {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
                if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }

    suspend fun download(url: String, dest: File, onProgress: (Float) -> Unit = {}): Unit =
        withContext(Dispatchers.IO) {
            val tmp = File(dest.parentFile, dest.name + ".part")
            val c = open(url)
            try {
                if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                val total = c.contentLengthLong
                var done = 0L
                var last = -1
                c.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != last) {
                                    last = pct
                                    onProgress(pct / 100f)
                                }
                            }
                        }
                    }
                }
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) error("no se pudo guardar el archivo")
            } finally {
                c.disconnect()
                if (tmp.exists()) tmp.delete()
            }
        }
}
