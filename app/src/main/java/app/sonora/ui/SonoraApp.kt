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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sonora.MainViewModel
import kotlinx.coroutines.delay

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Inicio", Icons.Rounded.Home),
    Search("Buscar", Icons.Rounded.Search),
    Library("Biblioteca", Icons.Rounded.LibraryMusic),
    Settings("Ajustes", Icons.Rounded.Settings)
}

@Composable
private fun BottomBar(tab: Tab, onTab: (Tab) -> Unit) {
    Row3(tab, onTab)
}

@Composable
private fun Row3(tab: Tab, onTab: (Tab) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .background(Surface1)
            .navigationBarsPadding()
            .height(62.dp)
    ) {
        Tab.values().forEach { t ->
            val selected = t == tab
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onTab(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Icon(t.icon, t.label, tint = if (selected) Brand else Muted)
                Text(t.label, fontSize = 11.sp, color = if (selected) Brand else Muted)
            }
        }
    }
}

@Composable
fun SonoraApp(vm: MainViewModel) {
    val ctx = LocalContext.current
    val songs by vm.songs.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val entries by vm.playlistEntries.collectAsStateWithLifecycle()
    val links by vm.links.collectAsStateWithLifecycle()
    val linkEntries by vm.linkEntries.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showPlayer by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(Tab.Home) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        vm.importSongs(it)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.importFolder(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val actions = remember {
        Actions(
            play = { list, i -> askNotifications(); vm.play(list, i) },
            shuffle = { list -> askNotifications(); vm.playShuffled(list) },
            dialog = { dlg = it },
            open = { detail = it },
            addMusic = { pickFiles.launch(arrayOf("audio/*", "application/ogg")) },
            addFolder = { pickFolder.launch(null) },
            exportBackup = { exportLauncher.launch("sonora-copia.json") },
            importBackup = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        )
    }

    // Progreso mientras suena
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
    BackHandler(enabled = !showPlayer && detail != null) { detail = null }
    BackHandler(enabled = !showPlayer && detail == null && tab != Tab.Home) { tab = Tab.Home }

    val current = remember(songs, vm.currentId) { songs.firstOrNull { it.id == vm.currentId } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BrandDeep, Bg), endY = 1100f))
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                val d = detail
                when {
                    d is Detail.PlaylistD -> {
                        val p = playlists.firstOrNull { it.id == d.id }
                        if (p == null) {
                            LaunchedEffect(Unit) { detail = null }
                        } else {
                            val byId = remember(songs) { songs.associateBy { it.id } }
                            val ids = entries[p.id].orEmpty()
                            val list = remember(ids, byId) { ids.mapNotNull { byId[it] } }
                            val linkById = remember(links) { links.associateBy { it.id } }
                            val linkList = linkEntries[p.id].orEmpty().mapNotNull { linkById[it] }
                            DetailScreen(
                                title = p.name,
                                subtitle = "Lista · ${list.size} canciones",
                                coverPath = list.firstNotNullOfOrNull { it.coverPath },
                                list = list,
                                vm = vm,
                                a = actions,
                                onBack = { detail = null },
                                topMenu = listOf(
                                    MenuAction("Renombrar") { dlg = Dlg.RenameList(p) },
                                    MenuAction("Eliminar lista") { dlg = Dlg.DeleteList(p) }
                                ),
                                extra = { s ->
                                    listOf(
                                        MenuAction("Subir en la lista") { vm.moveInPlaylist(p.id, s.id, -1) },
                                        MenuAction("Bajar en la lista") { vm.moveInPlaylist(p.id, s.id, 1) },
                                        MenuAction("Quitar de esta lista") { vm.removeFromPlaylist(p.id, s.id) }
                                    )
                                },
                                links = linkList,
                                linkMenu = { l ->
                                    listOf(
                                        MenuAction("Abrir en ${l.service}") { openExternal(ctx, l.url) },
                                        MenuAction("Quitar de esta lista") { vm.removeLinkFromPlaylist(p.id, l.id) }
                                    )
                                }
                            )
                        }
                    }
                    d is Detail.ArtistD -> {
                        val list = remember(songs, d.name) {
                            songs.filter { it.artist == d.name }.sortedBy { it.album + it.title }
                        }
                        DetailScreen(
                            title = d.name,
                            subtitle = "Artista · ${list.size} canciones",
                            coverPath = list.firstNotNullOfOrNull { it.coverPath },
                            list = list, vm = vm, a = actions, onBack = { detail = null }
                        )
                    }
                    d is Detail.AlbumD -> {
                        val list = remember(songs, d.name) {
                            songs.filter { it.album == d.name }.sortedBy { it.title }
                        }
                        DetailScreen(
                            title = d.name,
                            subtitle = "Álbum · ${list.size} canciones",
                            coverPath = list.firstNotNullOfOrNull { it.coverPath },
                            list = list, vm = vm, a = actions, onBack = { detail = null }
                        )
                    }
                    else -> when (tab) {
                        Tab.Home -> HomeScreen(vm, songs, playlists, entries, actions)
                        Tab.Search -> SearchScreen(vm, songs, playlists, actions)
                        Tab.Library -> LibraryScreen(vm, songs, playlists, entries, links, actions)
                        Tab.Settings -> SettingsScreen(vm, songs.size, actions)
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
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            BottomBar(tab) { tab = it; detail = null }
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 140.dp)
        )

        AnimatedVisibility(
            visible = showPlayer,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            PlayerScreen(song = current, vm = vm, songs = songs, onClose = { showPlayer = false })
        }
    }

    Dialogs(dlg, vm, playlists) { dlg = it }
}
