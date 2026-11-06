package app.sonora.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonora.MainViewModel
import app.sonora.data.KeyKind
import app.sonora.data.LinkRef
import app.sonora.data.Playlist
import app.sonora.data.Song
import coil.compose.AsyncImage
import java.io.File

// ---------- Modelo de navegación y acciones ----------

data class MenuAction(val label: String, val run: () -> Unit)

sealed interface Dlg {
    data class DeleteSong(val song: Song) : Dlg
    data class EditSong(val song: Song) : Dlg
    data class AddToList(val song: Song) : Dlg
    data class NewList(val thenAdd: Song?) : Dlg
    data class RenameList(val p: Playlist) : Dlg
    data class DeleteList(val p: Playlist) : Dlg
    data class AddLinkToList(val link: LinkRef) : Dlg
    data class EditKey(val which: KeyKind) : Dlg
    data class ConfirmDownload(val url: String, val name: String) : Dlg
}

sealed interface Detail {
    data class PlaylistD(val id: Long) : Detail
    data class ArtistD(val name: String) : Detail
    data class AlbumD(val name: String) : Detail
}

class Actions(
    val play: (List<Song>, Int) -> Unit,
    val shuffle: (List<Song>) -> Unit,
    val dialog: (Dlg) -> Unit,
    val open: (Detail) -> Unit,
    val addMusic: () -> Unit,
    val addFolder: () -> Unit,
    val exportBackup: () -> Unit,
    val importBackup: () -> Unit
)

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

// ---------- Piezas visuales ----------

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
fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
    )
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Brand else Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.Black else TextMain,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun PillButton(label: String, icon: ImageVector, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) Brand else Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (filled) Color.Black else Brand, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (filled) Color.Black else TextMain, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)).background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Brand, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, color = Muted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.Black)
            ) {
                Text(action, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick)
}

@Composable
fun SongRow(
    song: Song,
    active: Boolean,
    vm: MainViewModel,
    a: Actions,
    extra: List<MenuAction>,
    onClick: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(song.coverPath, Modifier.size(52.dp), 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.favorite) {
                    Icon(Icons.Rounded.Favorite, null, tint = Brand, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    song.title,
                    color = if (active) Brand else TextMain,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${song.artist} · ${fmt(song.durationMs)}",
                color = Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, "Más opciones", tint = Muted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuItem("Reproducir a continuación") { menu = false; vm.playNext(song) }
                MenuItem("Añadir a la cola") { menu = false; vm.addToQueue(song) }
                MenuItem(if (song.favorite) "Quitar de favoritos" else "Añadir a favoritos") {
                    menu = false
                    vm.toggleFavorite(song)
                }
                MenuItem("Añadir a una lista") { menu = false; a.dialog(Dlg.AddToList(song)) }
                extra.forEach { e -> MenuItem(e.label) { menu = false; e.run() } }
                MenuItem("Editar datos") { menu = false; a.dialog(Dlg.EditSong(song)) }
                MenuItem("Quitar de la biblioteca") { menu = false; a.dialog(Dlg.DeleteSong(song)) }
            }
        }
    }
}

@Composable
fun MiniPlayer(
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
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Pausa" else "Reproducir"
                    )
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

// ---------- Diálogos ----------

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brand,
    unfocusedBorderColor = Color(0xFF3A3A42),
    cursorColor = Brand,
    focusedLabelColor = Brand,
    unfocusedLabelColor = Muted
)

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Brand) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } }
    )
}

@Composable
fun TextDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    label: String = "Nombre",
    allowBlank: Boolean = false,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(label) },
                colors = fieldColors()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (allowBlank || text.isNotBlank()) {
                    onConfirm(text.trim())
                    onDismiss()
                }
            }) { Text(confirm, color = Brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } }
    )
}

@Composable
fun EditSongDialog(song: Song, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Editar datos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true, colors = fieldColors())
                OutlinedTextField(artist, { artist = it }, label = { Text("Artista") }, singleLine = true, colors = fieldColors())
                OutlinedTextField(album, { album = it }, label = { Text("Álbum") }, singleLine = true, colors = fieldColors())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(title.trim(), artist.trim(), album.trim())
                onDismiss()
            }) { Text("Guardar", color = Brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } }
    )
}

@Composable
fun AddToListDialog(
    title: String,
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onDismiss: () -> Unit,
    onNew: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                if (onNew != null) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = onNew).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = Brand)
                            Spacer(Modifier.width(12.dp))
                            Text("Nueva lista", color = Brand, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (playlists.isEmpty() && onNew == null) {
                    item { Text("Aún no tienes listas. Crea una desde la pestaña Listas de la biblioteca.", color = Muted) }
                }
                items(playlists, key = { it.id }) { p ->
                    Text(
                        p.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(p)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = Muted) } }
    )
}

@Composable
fun Dialogs(dlg: Dlg?, vm: MainViewModel, playlists: List<Playlist>, setDlg: (Dlg?) -> Unit) {
    val close = { setDlg(null) }
    when (dlg) {
        null -> Unit
        is Dlg.DeleteSong -> ConfirmDialog(
            "Quitar de la biblioteca",
            "«${dlg.song.title}» se quitará de SONORA. El archivo original no se borra.",
            "Quitar", close
        ) { vm.delete(dlg.song); close() }
        is Dlg.DeleteList -> ConfirmDialog(
            "Eliminar lista",
            "Se eliminará «${dlg.p.name}». Las canciones no se borran.",
            "Eliminar", close
        ) { vm.deletePlaylist(dlg.p.id); close() }
        is Dlg.NewList -> TextDialog("Nueva lista", "", "Crear", close) { name ->
            vm.createPlaylist(name, dlg.thenAdd)
        }
        is Dlg.RenameList -> TextDialog("Renombrar lista", dlg.p.name, "Guardar", close) { name ->
            vm.renamePlaylist(dlg.p.id, name)
        }
        is Dlg.EditSong -> EditSongDialog(dlg.song, close) { t, ar, al ->
            vm.updateInfo(dlg.song, t, ar, al)
        }
        is Dlg.AddToList -> AddToListDialog(
            "Añadir a una lista", playlists,
            { p -> vm.addToPlaylist(p.id, p.name, dlg.song) }, close
        ) { setDlg(Dlg.NewList(dlg.song)) }
        is Dlg.AddLinkToList -> AddToListDialog(
            "Añadir enlace a una lista", playlists,
            { p -> vm.addLinkToPlaylist(p.id, p.name, dlg.link.id) }, close, null
        )
        is Dlg.ConfirmDownload -> ConfirmDialog(
            "Descargar archivo",
            "Se descargará «${dlg.name}» a tu biblioteca. Confirma que tienes derecho a usar este archivo: es tuyo, tiene licencia libre o tienes permiso.",
            "Descargar", close
        ) { vm.downloadUrl(dlg.url, dlg.name); close() }
        is Dlg.EditKey -> {
            val current = when (dlg.which) {
                KeyKind.Jamendo -> vm.jamendoId.value
                KeyKind.SpotifyId -> vm.spotifyId.value
                KeyKind.SpotifySecret -> vm.spotifySecret.value
            }
            val title = when (dlg.which) {
                KeyKind.Jamendo -> "Client ID de Jamendo"
                KeyKind.SpotifyId -> "Client ID de Spotify"
                KeyKind.SpotifySecret -> "Client Secret de Spotify"
            }
            TextDialog(title, current, "Guardar", close, "Clave (vacío para borrar)", true) { v ->
                vm.setKey(dlg.which, v)
            }
        }
    }
}

// ---------- Imágenes remotas y enlaces externos ----------

@Composable
fun CoverUrl(url: String?, modifier: Modifier, radius: Dp) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(Surface2),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Muted)
        }
    }
}

fun openExternal(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "No hay ninguna app para abrir este enlace", Toast.LENGTH_SHORT).show()
    }
}
