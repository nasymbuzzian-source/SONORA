package app.sonora

import android.app.Application
import android.content.ComponentName
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
import app.sonora.data.AppDatabase
import app.sonora.data.Song
import app.sonora.data.SongImporter
import app.sonora.data.toMediaItem
import app.sonora.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).songDao()
    private var controller: MediaController? = null

    val songs: StateFlow<List<Song>> =
        dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var importing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    // Estado del reproductor (espejo del MediaController)
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
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun sync(p: Player) {
        isPlaying = p.isPlaying
        currentId = p.currentMediaItem?.mediaId?.toLongOrNull()
        duration = p.duration.coerceAtLeast(0L)
        position = p.currentPosition
        shuffle = p.shuffleModeEnabled
        repeat = p.repeatMode
    }

    // ---- Reproducción ----

    fun play(list: List<Song>, index: Int) {
        val c = controller ?: return
        c.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
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

    fun clearMessage() { message = null }

    fun toggleFavorite(s: Song) {
        viewModelScope.launch { dao.setFavorite(s.id, !s.favorite) }
    }

    fun delete(s: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            s.coverPath?.let { File(it).delete() }
            dao.delete(s.id)
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
