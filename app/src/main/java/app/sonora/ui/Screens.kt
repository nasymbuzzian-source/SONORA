package app.sonora.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sonora.MainViewModel
import app.sonora.data.Playlist
import app.sonora.data.Song
import java.util.Calendar

// ---------- Utilidades de pantalla ----------

@Composable
private fun ScreenHeader(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("SONORA", color = Brand, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text(title, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        }
        trailing()
    }
}

@Composable
private fun AddMenuButton(a: Actions) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Brand).clickable { open = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Add, "Añadir música", tint = Color.Black)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Añadir canciones") }, onClick = { open = false; a.addMusic() })
            DropdownMenuItem(text = { Text("Añadir carpeta") }, onClick = { open = false; a.addFolder() })
        }
    }
}

private fun LazyListScope.songItems(
    list: List<Song>,
    vm: MainViewModel,
    a: Actions,
    extra: (Song) -> List<MenuAction> = { emptyList() }
) {
    itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
        SongRow(s, s.id == vm.currentId, vm, a, extra(s)) { a.play(list, i) }
    }
}

@Composable
private fun GroupRow(
    title: String,
    subtitle: String,
    coverPath: String?,
    round: Boolean,
    onClick: () -> Unit,
    menu: List<MenuAction> = emptyList()
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(coverPath, Modifier.size(52.dp), if (round) 26.dp else 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (menu.isNotEmpty()) {
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "Más opciones", tint = Muted) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEach { m ->
                        DropdownMenuItem(text = { Text(m.label) }, onClick = { open = false; m.run() })
                    }
                }
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
    }
}

@Composable
private fun SongShelf(title: String, list: List<Song>, onPlay: (Int) -> Unit) {
    if (list.isEmpty()) return
    Column {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                Column(Modifier.width(132.dp).clickable { onPlay(i) }) {
                    Cover(s.coverPath, Modifier.size(132.dp), 14.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(s.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------- Inicio ----------

@Composable
fun HomeScreen(
    vm: MainViewModel,
    songs: List<Song>,
    playlists: List<Playlist>,
    entries: Map<Long, List<Long>>,
    a: Actions
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 6 -> "Buenas noches"
        hour < 13 -> "Buenos días"
        hour < 21 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    val recent = remember(songs) {
        songs.filter { it.lastPlayedAt > 0 }.sortedByDescending { it.lastPlayedAt }.take(12)
    }
    val favs = remember(songs) { songs.filter { it.favorite }.take(12) }
    val added = remember(songs) { songs.take(12) }
    val recommended = remember(songs) {
        val artists = recent.take(5).map { it.artist }.filter { it != "Artista desconocido" }.toSet()
        val recentIds = recent.take(10).map { it.id }.toSet()
        songs.filter { it.artist in artists && it.id !in recentIds }.shuffled().take(12)
    }
    val byId = remember(songs) { songs.associateBy { it.id } }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ScreenHeader(greeting) { AddMenuButton(a) } }
        if (songs.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.LibraryMusic,
                    "Aún no tienes música",
                    "Añade canciones de tu teléfono para empezar.",
                    "Añadir música",
                    a.addMusic
                )
            }
        } else {
            item {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Mezclar todo", Icons.Rounded.Shuffle, true, { a.shuffle(songs) })
                    PillButton("Reproducir", Icons.Rounded.PlayArrow, false, { a.play(songs, 0) })
                }
            }
            item { SongShelf("Reproducido recientemente", recent) { i -> a.play(recent, i) } }
            item { SongShelf("Tus favoritas", favs) { i -> a.play(favs, i) } }
            if (playlists.isNotEmpty()) {
                item {
                    Column {
                        SectionTitle("Tus listas")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(playlists, key = { it.id }) { p ->
                                val ids = entries[p.id].orEmpty()
                                Column(Modifier.width(132.dp).clickable { a.open(Detail.PlaylistD(p.id)) }) {
                                    Cover(ids.firstNotNullOfOrNull { byId[it]?.coverPath }, Modifier.size(132.dp), 14.dp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${ids.size} canciones", color = Muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
            item { SongShelf("Recomendado para ti", recommended) { i -> a.play(recommended, i) } }
            item { SongShelf("Añadido recientemente", added) { i -> a.play(added, i) } }
        }
    }
}

// ---------- Buscar ----------

@Composable
fun SearchScreen(vm: MainViewModel, songs: List<Song>, playlists: List<Playlist>, a: Actions) {
    var q by remember { mutableStateOf("") }
    val query = q.trim()
    val songHits = remember(songs, query) {
        if (query.isEmpty()) emptyList()
        else songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    }
    val artistHits = remember(songs, query) {
        if (query.isEmpty()) emptyList() else songs.map { it.artist }.distinct().filter { it.contains(query, true) }
    }
    val albumHits = remember(songs, query) {
        if (query.isEmpty()) emptyList() else songs.map { it.album }.distinct().filter { it.contains(query, true) }
    }
    val listHits = remember(playlists, query) {
        if (query.isEmpty()) emptyList() else playlists.filter { it.name.contains(query, true) }
    }
    val none = query.isNotEmpty() && songHits.isEmpty() && artistHits.isEmpty() && albumHits.isEmpty() && listHits.isEmpty()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Buscar")
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            placeholder = { Text("Canciones, artistas, álbumes o listas", color = Muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Muted) },
            trailingIcon = {
                if (q.isNotEmpty()) {
                    IconButton(onClick = { q = "" }) { Icon(Icons.Rounded.Close, "Borrar búsqueda", tint = Muted) }
                }
            },
            shape = RoundedCornerShape(28.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        )
        when {
            query.isEmpty() -> EmptyState(
                Icons.Rounded.Search,
                "Busca en tu música",
                "Escribe el nombre de una canción, un artista, un álbum o una lista."
            )
            none -> EmptyState(Icons.Rounded.Search, "Sin resultados", "No hay nada que coincida con «$query».")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                if (artistHits.isNotEmpty()) {
                    item { SectionTitle("Artistas") }
                    items(artistHits, key = { "artist:$it" }) { name ->
                        val list = songs.filter { it.artist == name }
                        GroupRow(name, "${list.size} canciones", list.firstNotNullOfOrNull { it.coverPath }, true,
                            { a.open(Detail.ArtistD(name)) })
                    }
                }
                if (albumHits.isNotEmpty()) {
                    item { SectionTitle("Álbumes") }
                    items(albumHits, key = { "album:$it" }) { name ->
                        val list = songs.filter { it.album == name }
                        GroupRow(name, "${list.firstOrNull()?.artist ?: ""} · ${list.size} canciones",
                            list.firstNotNullOfOrNull { it.coverPath }, false, { a.open(Detail.AlbumD(name)) })
                    }
                }
                if (listHits.isNotEmpty()) {
                    item { SectionTitle("Listas") }
                    items(listHits, key = { "list:${it.id}" }) { p ->
                        GroupRow(p.name, "Lista de reproducción", null, false, { a.open(Detail.PlaylistD(p.id)) })
                    }
                }
                if (songHits.isNotEmpty()) {
                    item { SectionTitle("Canciones") }
                    songItems(songHits, vm, a)
                }
            }
        }
    }
}

// ---------- Biblioteca ----------

private enum class LibTab(val label: String) {
    Songs("Canciones"), Recent("Recientes"), Favs("Favoritos"),
    Lists("Listas"), Artists("Artistas"), Albums("Álbumes")
}

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    songs: List<Song>,
    playlists: List<Playlist>,
    entries: Map<Long, List<Long>>,
    a: Actions
) {
    var tab by remember { mutableStateOf(LibTab.Songs) }
    val byId = remember(songs) { songs.associateBy { it.id } }
    val sorted = remember(songs) { songs.sortedBy { it.title.lowercase() } }
    val favs = remember(songs) { songs.filter { it.favorite } }
    val artists = remember(songs) { songs.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() } }
    val albums = remember(songs) { songs.groupBy { it.album }.toList().sortedBy { it.first.lowercase() } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Tu biblioteca") { AddMenuButton(a) }
        if (vm.importing) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                color = Brand,
                trackColor = Surface2
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LibTab.values().toList()) { t -> Chip(t.label, t == tab) { tab = t } }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            when (tab) {
                LibTab.Songs -> {
                    if (songs.isEmpty()) item {
                        EmptyState(Icons.Rounded.LibraryMusic, "Tu biblioteca está vacía",
                            "Añade canciones de tu teléfono: MP3, FLAC, WAV, M4A, AAC u OGG.", "Añadir música", a.addMusic)
                    } else songItems(sorted, vm, a)
                }
                LibTab.Recent -> {
                    if (songs.isEmpty()) item {
                        EmptyState(Icons.Rounded.LibraryMusic, "Nada por aquí", "Las canciones que añadas aparecerán aquí.")
                    } else songItems(songs, vm, a)
                }
                LibTab.Favs -> {
                    if (favs.isEmpty()) item {
                        EmptyState(Icons.Rounded.Favorite, "Aún no tienes favoritos",
                            "Marca el corazón en el reproductor o usa el menú ⋮ de una canción.")
                    } else songItems(favs, vm, a)
                }
                LibTab.Lists -> {
                    item {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            PillButton("Nueva lista", Icons.Rounded.Add, true, { a.dialog(Dlg.NewList(null)) })
                        }
                    }
                    if (playlists.isEmpty()) item {
                        EmptyState(Icons.Rounded.LibraryMusic, "Sin listas todavía",
                            "Crea una lista y añade tus canciones desde el menú ⋮.")
                    } else items(playlists, key = { it.id }) { p ->
                        val ids = entries[p.id].orEmpty()
                        GroupRow(
                            p.name, "${ids.size} canciones",
                            ids.firstNotNullOfOrNull { byId[it]?.coverPath }, false,
                            { a.open(Detail.PlaylistD(p.id)) },
                            listOf(
                                MenuAction("Renombrar") { a.dialog(Dlg.RenameList(p)) },
                                MenuAction("Eliminar lista") { a.dialog(Dlg.DeleteList(p)) }
                            )
                        )
                    }
                }
                LibTab.Artists -> {
                    if (artists.isEmpty()) item {
                        EmptyState(Icons.Rounded.LibraryMusic, "Sin artistas", "Añade música para verla agrupada por artista.")
                    } else items(artists, key = { it.first }) { (name, list) ->
                        GroupRow(name, "${list.size} canciones", list.firstNotNullOfOrNull { it.coverPath }, true,
                            { a.open(Detail.ArtistD(name)) })
                    }
                }
                LibTab.Albums -> {
                    if (albums.isEmpty()) item {
                        EmptyState(Icons.Rounded.LibraryMusic, "Sin álbumes", "Añade música para verla agrupada por álbum.")
                    } else items(albums, key = { it.first }) { (name, list) ->
                        GroupRow(name, "${list.first().artist} · ${list.size} canciones",
                            list.firstNotNullOfOrNull { it.coverPath }, false, { a.open(Detail.AlbumD(name)) })
                    }
                }
            }
        }
    }
}

// ---------- Detalle (lista, artista, álbum) ----------

@Composable
fun DetailScreen(
    title: String,
    subtitle: String,
    coverPath: String?,
    list: List<Song>,
    vm: MainViewModel,
    a: Actions,
    onBack: () -> Unit,
    topMenu: List<MenuAction> = emptyList(),
    extra: (Song) -> List<MenuAction> = { emptyList() }
) {
    var menu by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") }
                Spacer(Modifier.weight(1f))
                if (topMenu.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            topMenu.forEach { m ->
                                DropdownMenuItem(text = { Text(m.label) }, onClick = { menu = false; m.run() })
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(coverPath, Modifier.size(180.dp), 20.dp)
                Spacer(Modifier.height(16.dp))
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Muted)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Reproducir", Icons.Rounded.PlayArrow, true, { a.play(list, 0) })
                    PillButton("Aleatorio", Icons.Rounded.Shuffle, false, { a.shuffle(list) })
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (list.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.LibraryMusic, "Aquí no hay canciones",
                    "Añade canciones desde el menú ⋮ de cualquier canción de tu biblioteca.")
            }
        } else {
            songItems(list, vm, a, extra)
        }
    }
}

// ---------- Ajustes ----------

@Composable
private fun SettingsTitle(text: String) {
    Text(
        text.uppercase(),
        color = Brand,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        trailing()
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, songCount: Int, a: Actions) {
    val ctx = LocalContext.current
    val accent by vm.accent.collectAsStateWithLifecycle()
    val restore by vm.restoreQueue.collectAsStateWithLifecycle()
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val notifOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader("Ajustes")

        SettingsTitle("Apariencia")
        SettingRow("Color de identidad", ACCENTS[accent.coerceIn(0, ACCENTS.lastIndex)].first)
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ACCENTS.forEachIndexed { i, (_, color) ->
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(if (i == accent) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                        .clickable { vm.setAccent(i) }
                )
            }
        }

        SettingsTitle("Reproductor")
        SettingRow("Recordar la cola y la posición", "Al reabrir SONORA, retoma donde lo dejaste.") {
            Switch(
                checked = restore,
                onCheckedChange = { vm.setRestoreQueue(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Brand, checkedThumbColor = Color.Black)
            )
        }
        SettingRow("Calidad de reproducción", "Original. Tus archivos suenan tal cual, sin recodificar.")

        SettingsTitle("Biblioteca")
        SettingRow("Canciones en SONORA", "$songCount canciones")
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Añadir canciones", Icons.Rounded.Add, true, { a.addMusic() })
            PillButton("Añadir carpeta", Icons.Rounded.Add, false, { a.addFolder() })
            PillButton("Quitar archivos que ya no existen", Icons.Rounded.Close, false, { vm.cleanMissing() })
        }

        SettingsTitle("Permisos")
        SettingRow(
            "Notificaciones",
            if (notifOn) "Activadas: controles en la pantalla de bloqueo."
            else "Desactivadas: sin controles en la pantalla de bloqueo."
        ) {
            PillButton("Ajustes", Icons.Rounded.MoreVert, false, {
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                )
            })
        }
        SettingRow("Archivos de música", "SONORA solo accede a los archivos y carpetas que tú eliges.")

        SettingsTitle("Copia de seguridad")
        SettingRow("Preferencias, favoritos y listas", "Se guardan en un archivo que tú eliges.")
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Exportar", Icons.Rounded.Add, true, { a.exportBackup() })
            PillButton("Importar", Icons.Rounded.Add, false, { a.importBackup() })
        }

        SettingsTitle("Acerca de")
        SettingRow("SONORA $version", "Reproductor personal. Sin anuncios, sin rastreadores. Tu música y tus datos se quedan en el dispositivo.")
    }
}
