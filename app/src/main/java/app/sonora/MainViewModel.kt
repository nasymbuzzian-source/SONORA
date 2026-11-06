package app.sonora

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.room.withTransaction
import app.sonora.data.AppDatabase
import app.sonora.data.Playlist
import app.sonora.data.PlaylistSong
import app.sonora.data.SettingsStore
import app.sonora.data.Song
import app.sonora.data.SongImporter
import app.sonora.data.toMediaItem
import app.sonora.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val dao = db.songDao()
    private val pdao = db.playlistDao()
    private val store = SettingsStore(app)
    private var controller: MediaController? = null
    private var lastMarkedId: Long? = null
    private var tickCount = 0

    // ---- Datos ----
    val songs: StateFlow<List<Song>> =
        dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists: StateFlow<List<Playlist>> =
        pdao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlistEntries: StateFlow<Map<Long, List<Long>>> =
        pdao.allEntries()
            .map { list -> list.groupBy({ it.playlistId }, { it.songId }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ---- Ajustes ----
    val accent: StateFlow<Int> = store.accent.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val restoreQueue: StateFlow<Boolean> = store.restore.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    var importing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    // ---- Estado del reproductor (espejo del MediaController) ----
    var isPlaying by mutableStateOf(false)
        private set
    var currentId by mutableStateOf<Long?>(null)
        private set
    var position by mutableStateOf(0L)
        private set
    var duration by mutableStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableStateOf(Player.REPEAT_MODE_OFF)
        private set
    var queue by mutableStateOf<List<Long>>(emptyList())
        private set
    var queueIndex by mutableStateOf(0)
        private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { c ->
                controller = c
                c.addListener(listener)
                sync(c)
                viewModelScope.launch { restoreState(c) }
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun sync(p: Player) {
        val newId = p.currentMediaItem?.mediaId?.toLongOrNull()
        if (p.isPlaying && newId != null && newId != lastMarkedId) {
            lastMarkedId = newId
            viewModelScope.launch { dao.markPlayed(newId, System.currentTimeMillis()) }
        }
        isPlaying = p.isPlaying
        currentId = newId
        duration = p.duration.coerceAtLeast(0L)
        position = p.currentPosition
        shuffle = p.shuffleModeEnabled
        repeat = p.repeatMode
        queue = List(p.mediaItemCount) { p.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L }
        queueIndex = p.currentMediaItemIndex
        if (!p.isPlaying) saveState()
    }

    // ---- Persistencia del estado del reproductor ----
    private fun saveState() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val ids = List(c.mediaItemCount) { c.getMediaItemAt(it).mediaId }
        val raw = "${ids.joinToString(",")};${c.currentMediaItemIndex};${c.currentPosition};${c.shuffleModeEnabled};${c.repeatMode}"
        viewModelScope.launch { store.saveState(raw) }
    }

    private suspend fun restoreState(c: MediaController) {
        if (c.mediaItemCount > 0 || !store.restore.first()) return
        val raw = store.loadState() ?: return
        val parts = raw.split(";")
        if (parts.size < 5) return
        val ids = parts[0].split(",").mapNotNull { it.toLongOrNull() }
        val savedCurrent = ids.getOrNull(parts[1].toIntOrNull() ?: 0) ?: return
        val byId = dao.allNow().associateBy { it.id }
        val found = ids.mapNotNull { byId[it] }
        val idx = found.indexOfFirst { it.id == savedCurrent }
        if (idx < 0 || c.mediaItemCount > 0) return
        c.setMediaItems(found.map { it.toMediaItem() }, idx, parts[2].toLongOrNull() ?: 0L)
        c.shuffleModeEnabled = parts[3].toBoolean()
        c.repeatMode = parts[4].toIntOrNull() ?: Player.REPEAT_MODE_OFF
        c.prepare()
    }

    // ---- Reproducción ----
    fun play(list: List<Song>, index: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { it.toMediaItem() }, index.coerceIn(0, list.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun playShuffled(list: List<Song>) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.shuffleModeEnabled = true
        c.setMediaItems(list.map { it.toMediaItem() }, list.indices.random(), 0L)
        c.prepare()
        c.play()
    }

    fun playNext(s: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(s), 0)
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, s.toMediaItem())
            message = "«${s.title}» sonará a continuación"
        }
    }

    fun addToQueue(s: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(s), 0)
        } else {
            c.addMediaItem(s.toMediaItem())
            message = "«${s.title}» añadida a la cola"
        }
    }

    fun removeFromQueue(index: Int) { controller?.removeMediaItem(index) }

    fun jumpTo(index: Int) {
        controller?.let {
            it.seekTo(index, 0L)
            it.play()
        }
    }

    fun togglePlay() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() { controller?.seekToNext() }

    fun previous() { controller?.seekToPrevious() }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        position = ms
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun tick() {
        controller?.let { position = it.currentPosition }
        tickCount++
        if (tickCount % 20 == 0) saveState()
    }

    // ---- Biblioteca ----
    fun importSongs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing = true
            var ok = 0
            uris.forEach { if (SongImporter.importOne(app, it, dao)) ok++ }
            importing = false
            val bad = uris.size - ok
            message = if (bad == 0) "Añadidas $ok canciones"
            else "Añadidas $ok de ${uris.size}. $bad duplicadas o ilegibles"
        }
    }

    fun importFolder(tree: Uri) {
        viewModelScope.launch {
            importing = true
            try {
                app.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            val files = withContext(Dispatchers.IO) { SongImporter.listAudio(app, tree) }
            var ok = 0
            files.forEach { if (SongImporter.importOne(app, it, dao)) ok++ }
            importing = false
            message = if (files.isEmpty()) "No se encontró música en esa carpeta"
            else "Carpeta: añadidas $ok de ${files.size}"
        }
    }

    fun cleanMissing() {
        viewModelScope.launch {
            importing = true
            val removed = withContext(Dispatchers.IO) {
                var n = 0
                dao.allNow().forEach { s ->
                    val ok = runCatching {
                        app.contentResolver.openFileDescriptor(Uri.parse(s.uri), "r")?.use { true } ?: false
                    }.getOrDefault(false)
                    if (!ok) {
                        s.coverPath?.let { File(it).delete() }
                        dao.delete(s.id)
                        n++
                    }
                }
                n
            }
            importing = false
            message = if (removed == 0) "Todas las canciones siguen disponibles"
            else "Quitadas $removed canciones cuyo archivo ya no existe"
        }
    }

    fun clearMessage() { message = null }

    fun toggleFavorite(s: Song) {
        viewModelScope.launch { dao.setFavorite(s.id, !s.favorite) }
    }

    fun updateInfo(s: Song, title: String, artist: String, album: String) {
        viewModelScope.launch {
            dao.updateInfo(
                s.id,
                title.ifBlank { s.title },
                artist.ifBlank { s.artist },
                album.ifBlank { s.album }
            )
            message = "Datos actualizados"
        }
    }

    fun delete(s: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            s.coverPath?.let { File(it).delete() }
            dao.delete(s.id)
        }
    }

    // ---- Listas de reproducción ----
    fun createPlaylist(name: String, thenAdd: Song? = null) {
        viewModelScope.launch {
            val pid = pdao.insert(Playlist(name = name))
            if (thenAdd != null) {
                pdao.addEntry(PlaylistSong(pid, thenAdd.id, 0))
                message = "Lista «$name» creada con «${thenAdd.title}»"
            } else {
                message = "Lista «$name» creada"
            }
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch { pdao.rename(id, name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { pdao.delete(id) }
    }

    fun addToPlaylist(pid: Long, name: String, s: Song) {
        viewModelScope.launch {
            val exists = pdao.entriesNow().any { it.playlistId == pid && it.songId == s.id }
            if (exists) {
                message = "Ya está en «$name»"
            } else {
                pdao.addEntry(PlaylistSong(pid, s.id, pdao.nextPosition(pid)))
                message = "Añadida a «$name»"
            }
        }
    }

    fun removeFromPlaylist(pid: Long, sid: Long) {
        viewModelScope.launch { pdao.removeEntry(pid, sid) }
    }

    fun moveInPlaylist(pid: Long, sid: Long, delta: Int) {
        viewModelScope.launch {
            val ids = pdao.entriesNow().filter { it.playlistId == pid }.map { it.songId }.toMutableList()
            val i = ids.indexOf(sid)
            val j = i + delta
            if (i < 0 || j < 0 || j > ids.lastIndex) return@launch
            ids.add(j, ids.removeAt(i))
            db.withTransaction {
                ids.forEachIndexed { k, id -> pdao.setPosition(pid, id, k) }
            }
        }
    }

    // ---- Ajustes y copia de seguridad ----
    fun setAccent(i: Int) {
        viewModelScope.launch { store.setAccent(i) }
    }

    fun setRestoreQueue(b: Boolean) {
        viewModelScope.launch { store.setRestore(b) }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val all = dao.allNow()
                val byId = all.associateBy { it.id }
                val entries = pdao.entriesNow()
                val root = JSONObject()
                root.put("version", 1)
                root.put("accent", store.accent.first())
                root.put("restoreQueue", store.restore.first())
                root.put("favorites", JSONArray(all.filter { it.favorite }.map { it.uri }))
                val lists = JSONArray()
                pdao.allNow().forEach { p ->
                    val o = JSONObject()
                    o.put("name", p.name)
                    o.put(
                        "songs",
                        JSONArray(entries.filter { it.playlistId == p.id }.mapNotNull { byId[it.songId]?.uri })
                    )
                    lists.put(o)
                }
                root.put("playlists", lists)
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) }
                }
            }
            message = if (result.isSuccess) "Copia de seguridad guardada" else "No se pudo guardar la copia"
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: error("sin datos")
                val root = JSONObject(text)
                store.setAccent(root.optInt("accent", 0))
                store.setRestore(root.optBoolean("restoreQueue", true))
                val favs = root.optJSONArray("favorites")
                if (favs != null) for (i in 0 until favs.length()) dao.favoriteByUri(favs.getString(i))
                val byUri = dao.allNow().associateBy { it.uri }
                val existing = pdao.allNow().map { it.name }.toSet()
                var made = 0
                val lists = root.optJSONArray("playlists")
                if (lists != null) for (i in 0 until lists.length()) {
                    val o = lists.getJSONObject(i)
                    val name = o.getString("name")
                    if (name in existing) continue
                    val pid = pdao.insert(Playlist(name = name))
                    val arr = o.optJSONArray("songs")
                    var pos = 0
                    if (arr != null) for (k in 0 until arr.length()) {
                        val s = byUri[arr.getString(k)] ?: continue
                        pdao.addEntry(PlaylistSong(pid, s.id, pos++))
                    }
                    made++
                }
                made
            }
            message = result.fold(
                { "Copia restaurada ($it listas nuevas)" },
                { "No se pudo leer la copia de seguridad" }
            )
        }
    }

    override fun onCleared() {
        saveState()
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
