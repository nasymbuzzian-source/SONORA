package app.sonora.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sonora.MainViewModel
import app.sonora.data.LinkInfo
import app.sonora.data.LinkRef
import app.sonora.data.Playlist
import app.sonora.data.RemoteTrack
import app.sonora.data.Song

private enum class SearchMode(val label: String) {
    Mine("Mi música"), Links("Enlaces"), Free("Música libre")
}

@Composable
fun SearchScreen(vm: MainViewModel, songs: List<Song>, playlists: List<Playlist>, a: Actions) {
    var mode by remember { mutableStateOf(SearchMode.Mine) }
    var freeQuery by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Buscar")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SearchMode.values().toList()) { m -> Chip(m.label, m == mode) { mode = m } }
        }
        Spacer(Modifier.height(8.dp))
        when (mode) {
            SearchMode.Mine -> LocalSearch(vm, songs, playlists, a)
            SearchMode.Links -> LinksPanel(vm, a) { q ->
                freeQuery = q
                mode = SearchMode.Free
                vm.searchFree(q)
            }
            SearchMode.Free -> FreePanel(vm, freeQuery) { freeQuery = it }
        }
    }
}

// ---------- Mi música ----------

@Composable
private fun LocalSearch(vm: MainViewModel, songs: List<Song>, playlists: List<Playlist>, a: Actions) {
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

    Column(Modifier.fillMaxSize()) {
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

// ---------- Enlaces ----------

@Composable
private fun LinksPanel(vm: MainViewModel, a: Actions, onFindFree: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    val info = vm.linkInfo

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text("Pega un enlace de Spotify, YouTube o YouTube Music", color = Muted) },
            trailingIcon = {
                IconButton(onClick = {
                    val pasted = clipboard.getText()?.text
                    if (!pasted.isNullOrBlank()) {
                        text = pasted
                        vm.resolveLink(pasted)
                    }
                }) { Icon(Icons.Rounded.ContentPaste, "Pegar y analizar", tint = Brand) }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.resolveLink(text) }),
            shape = RoundedCornerShape(28.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 20.dp)) {
            PillButton("Analizar enlace", Icons.Rounded.Search, true, { vm.resolveLink(text) })
        }
        if (vm.linkLoading) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp), color = Brand, trackColor = Surface2)
        }
        vm.linkError?.let {
            Text(it, color = Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
        if (info != null) {
            LinkCard(info, vm, a, onFindFree)
        } else if (!vm.linkLoading) {
            Text(
                "SONORA lee el título y la portada del enlace y te deja guardarlo como referencia o abrirlo en su app. " +
                    "No descarga audio de Spotify, YouTube ni YouTube Music: para tener una canción dentro de SONORA usa tus propios archivos " +
                    "o la pestaña Música libre, que busca versiones con licencia libre.",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun LinkCard(info: LinkInfo, vm: MainViewModel, a: Actions, onFindFree: (String) -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .padding(20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverUrl(info.thumbnail, Modifier.size(96.dp), 14.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${info.service} · ${info.kind}", color = Brand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(info.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(info.artist, info.album, info.durationMs?.let { fmt(it) }).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, color = Muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (info.isAudioFile) {
            PillButton(
                "Descargar a mi biblioteca", Icons.Rounded.Add, true,
                { a.dialog(Dlg.ConfirmDownload(info.url, info.title)) }, Modifier.fillMaxWidth()
            )
        } else {
            PillButton(
                "Abrir en ${info.service}", Icons.Rounded.OpenInNew, true,
                { openExternal(ctx, info.url) }, Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            PillButton("Guardar en SONORA", Icons.Rounded.Add, false, { vm.saveLink(info) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            PillButton(
                "Buscar versión libre", Icons.Rounded.Search, false,
                { onFindFree(listOfNotNull(info.title, info.artist).joinToString(" ")) }, Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun LinkRow(link: LinkRef, menu: List<MenuAction>, onClick: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverUrl(link.thumbnail, Modifier.size(52.dp), 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(link.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(link.artist, link.service).joinToString(" · "),
                color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
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

// ---------- Música libre ----------

@Composable
private fun FreePanel(vm: MainViewModel, query: String, onQuery: (String) -> Unit) {
    val jamendo by vm.jamendoId.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    placeholder = { Text("Título, artista o género", color = Muted) },
                    trailingIcon = {
                        IconButton(onClick = { vm.searchFree(query) }) {
                            Icon(Icons.Rounded.Search, "Buscar", tint = Brand)
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.searchFree(query) }),
                    shape = RoundedCornerShape(28.dp),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
                Text(
                    "Fuentes: Internet Archive (solo Creative Commons y dominio público)" +
                        if (jamendo.isBlank()) ". Para activar Jamendo, añade tu Client ID en Ajustes."
                        else " y Jamendo (solo pistas con descarga permitida).",
                    color = Muted, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
                val dl = vm.downloading
                if (dl != null) {
                    Text("Descargando «$dl»…", modifier = Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(
                        progress = { vm.downloadProgress ?: 0f },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        color = Brand,
                        trackColor = Surface2
                    )
                }
                if (vm.freeLoading) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        color = Brand,
                        trackColor = Surface2
                    )
                }
                vm.freeError?.let {
                    Text(it, color = Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
            }
        }
        items(vm.freeResults, key = { it.source + it.id }) { t -> RemoteRow(t) { vm.downloadTrack(t) } }
    }
}

@Composable
private fun RemoteRow(t: RemoteTrack, onDownload: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverUrl(t.coverUrl, Modifier.size(52.dp), 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${t.artist} · ${t.album}", color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(t.source, t.license).joinToString(" · "),
                color = Brand, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onDownload) { Text("Añadir", color = Brand, fontWeight = FontWeight.Bold) }
    }
}
