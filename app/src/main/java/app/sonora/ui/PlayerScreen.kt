package app.sonora.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.sonora.MainViewModel
import app.sonora.data.Song

@Composable
fun PlayerScreen(song: Song?, vm: MainViewModel, songs: List<Song>, onClose: () -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    val total = vm.duration.coerceAtLeast(1L).toFloat()
    val shown = (drag ?: vm.position.toFloat()).coerceIn(0f, total)
    val fav = song?.favorite == true

    BackHandler(enabled = showQueue) { showQueue = false }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BrandDeep, Bg)))
                // consume toques para no activar la pantalla que queda debajo
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Cerrar reproductor", Modifier.size(32.dp))
                }
                Text("Reproduciendo", Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, fontSize = 13.sp)
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.Rounded.QueueMusic, "Cola de reproducción")
                }
            }

            Spacer(Modifier.weight(1f))
            Cover(song?.coverPath, Modifier.fillMaxWidth().aspectRatio(1f), 24.dp)
            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song?.title ?: "—",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (song != null) "${song.artist} · ${song.album}" else "",
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { song?.let { vm.toggleFavorite(it) } }) {
                    Icon(
                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (fav) "Quitar de favoritos" else "Añadir a favoritos",
                        tint = if (fav) Brand else Muted
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Slider(
                value = shown,
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { vm.seekTo(it.toLong()) }
                    drag = null
                },
                valueRange = 0f..total,
                colors = SliderDefaults.colors(
                    thumbColor = Brand,
                    activeTrackColor = Brand,
                    inactiveTrackColor = Color(0xFF3A3A42)
                )
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(shown.toLong()), color = Muted, fontSize = 12.sp)
                Text(fmt(vm.duration), color = Muted, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(Icons.Rounded.Shuffle, "Aleatorio", tint = if (vm.shuffle) Brand else Muted)
                }
                IconButton(onClick = { vm.previous() }, Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Anterior", Modifier.size(38.dp))
                }
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(Brand).clickable { vm.togglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (vm.isPlaying) "Pausa" else "Reproducir",
                        Modifier.size(44.dp),
                        tint = Color.Black
                    )
                }
                IconButton(onClick = { vm.next() }, Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Siguiente", Modifier.size(38.dp))
                }
                IconButton(onClick = { vm.cycleRepeat() }) {
                    Icon(
                        if (vm.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repetir",
                        tint = if (vm.repeat != Player.REPEAT_MODE_OFF) Brand else Muted
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        AnimatedVisibility(
            visible = showQueue,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            QueuePanel(vm, songs) { showQueue = false }
        }
    }
}

@Composable
private fun QueuePanel(vm: MainViewModel, songs: List<Song>, onClose: () -> Unit) {
    val byId = remember(songs) { songs.associateBy { it.id } }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = vm.queueIndex.coerceAtLeast(0))
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Cola de reproducción", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("${vm.queue.size} canciones", color = Muted, fontSize = 13.sp)
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cerrar cola") }
        }
        LazyColumn(state = state) {
            itemsIndexed(vm.queue) { i, id ->
                val s = byId[id]
                if (s != null) {
                    val active = i == vm.queueIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.jumpTo(i) }
                            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(s.coverPath, Modifier.size(46.dp), 8.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title,
                                color = if (active) Brand else TextMain,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(s.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { vm.removeFromQueue(i) }) {
                            Icon(Icons.Rounded.Close, "Quitar de la cola", tint = Muted)
                        }
                    }
                }
            }
        }
    }
}
