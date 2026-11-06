package app.sonora.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sonora.MainViewModel
import app.sonora.data.Song
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun SonoraApp(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    val songs by vm.songs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showPlayer by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Song?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        vm.importSongs(it)
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val addMusic = { picker.launch(arrayOf("audio/*", "application/ogg")) }

    fun play(index: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.play(songs, index)
    }

    // Actualiza el progreso mientras suena
    LaunchedEffect(vm.isPlaying) {
        while (vm.isPlaying) {
            vm.tick()
            delay(500)
        }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }
    BackHandler(enabled = showPlayer) { showPlayer = false }

    val current = songs.firstOrNull { it.id == vm.currentId }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B2A20), Bg), endY = 1100f))
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("SONORA", color = Brand, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("Tu biblioteca", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Brand).clickable { addMusic() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Add, "Añadir música", tint = Color.Black)
                }
            }

            if (vm.importing) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    color = Brand,
                    trackColor = Surface2
                )
            }

            if (songs.isEmpty()) {
                EmptyState(onAdd = { addMusic() })
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 130.dp)) {
                    itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                        SongRow(
                            song = s,
                            active = s.id == vm.currentId,
                            onClick = { play(i) },
                            onDelete = { toDelete = s }
                        )
                    }
                }
            }
        }

        if (current != null) {
            MiniPlayer(
                song = current,
                playing = vm.isPlaying,
                progress = if (vm.duration > 0) (vm.position.toFloat() / vm.duration).coerceIn(0f, 1f) else 0f,
                onToggle = { vm.togglePlay() },
                onOpen = { showPlayer = true },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp)
            )
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp)
        )

        AnimatedVisibility(
            visible = showPlayer,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            PlayerScreen(song = current, vm = vm, onClose = { showPlayer = false })
        }
    }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Surface2,
            title = { Text("Quitar de la biblioteca") },
            text = { Text("«${s.title}» se quitará de SONORA. El archivo original no se borra.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(s); toDelete = null }) { Text("Quitar", color = Brand) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancelar", color = Muted) }
            }
        )
    }
}

@Composable
fun Cover(path: String?, modifier: Modifier, radius: Dp) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(Surface2),
        contentAlignment = Alignment.Center
    ) {
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Muted)
        }
    }
}

@Composable
private fun SongRow(song: Song, active: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(song.coverPath, Modifier.size(52.dp), 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (active) Brand else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${song.artist} · ${fmt(song.durationMs)}",
                color = Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, "Quitar de la biblioteca", tint = Muted)
        }
    }
}

@Composable
private fun MiniPlayer(
    song: Song,
    playing: Boolean,
    progress: Float,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier.padding(horizontal = 10.dp).fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        color = Surface2
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(song.coverPath, Modifier.size(44.dp), 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onToggle) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pausa" else "Reproducir")
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Brand,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(Surface2), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.LibraryMusic, null, tint = Brand, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Tu biblioteca está vacía", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Añade canciones de tu teléfono: MP3, FLAC, WAV, M4A, AAC u OGG.",
            color = Muted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.Black)
        ) {
            Text("Añadir música", fontWeight = FontWeight.SemiBold)
        }
    }
}
