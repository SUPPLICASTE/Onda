package com.supplicaste.onda.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.supplicaste.onda.data.LIKED_PLAYLIST_ID
import com.supplicaste.onda.ui.home.HomeScreen
import com.supplicaste.onda.ui.library.CollectionDialog
import com.supplicaste.onda.ui.library.LibraryScreen
import com.supplicaste.onda.ui.login.LoginScreen
import com.supplicaste.onda.ui.player.MiniPlayer
import com.supplicaste.onda.ui.player.NowPlayingScreen
import com.supplicaste.onda.ui.profile.ProfileDialog
import com.supplicaste.onda.ui.search.SearchScreen
import com.supplicaste.onda.ui.theme.OndaTheme

private enum class OndaTab(val label: String, val icon: ImageVector) {
    Home("Inicio", Icons.Filled.Home),
    Search("Buscar", Icons.Filled.Search),
    Library("Biblioteca", Icons.Filled.LibraryMusic)
}

private data class CollectionTarget(val id: Long, val name: String, val isAlbum: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OndaApp(vm: OndaViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    OndaTheme(darkTheme = state.darkTheme) {
        val snackbar = remember { SnackbarHostState() }
        var tab by remember { mutableStateOf(OndaTab.Home) }
        var showPlayer by remember { mutableStateOf(false) }
        var showProfile by remember { mutableStateOf(false) }
        var createPlaylist by remember { mutableStateOf(false) }
        var createAlbum by remember { mutableStateOf(false) }
        var renameTarget by remember { mutableStateOf<CollectionTarget?>(null) }
        var collection by remember { mutableStateOf<CollectionTarget?>(null) }

        LaunchedEffect(state.message) {
            if (state.message.isNotBlank()) {
                snackbar.showSnackbar(state.message)
                vm.clearMessage()
            }
        }

        if (state.email == null) {
            LoginScreen(
                vm = vm,
                message = state.message,
                loading = state.loading,
                mode = state.authMode,
                googleAvailable = state.googleAvailable,
                spotifyAvailable = state.spotifyAvailable
            )
            return@OndaTheme
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "onda",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    },
                    actions = {
                        if (tab != OndaTab.Search) {
                            IconButton(onClick = { tab = OndaTab.Search }) {
                                Icon(Icons.Filled.Search, contentDescription = "Buscar música")
                            }
                        }
                        IconButton(onClick = { showProfile = true }) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = "Tu perfil")
                        }
                    }
                )
            },
            bottomBar = {
                Column {
                    MiniPlayer(state = state, vm = vm, onExpand = { showPlayer = true })
                    NavigationBar {
                        OndaTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            val inner = Modifier.padding(padding).fillMaxSize()
            when (tab) {
                OndaTab.Home -> HomeScreen(
                    modifier = inner,
                    state = state,
                    vm = vm,
                    onOpenAlbum = { id, name -> collection = CollectionTarget(id, name, true) },
                    onOpenPlaylist = { id, name -> collection = CollectionTarget(id, name, false) },
                    onOpenLiked = {
                        collection = CollectionTarget(LIKED_PLAYLIST_ID, "Me gusta", false)
                    },
                    onSearchArtist = { artist ->
                        tab = OndaTab.Search
                        vm.search(artist)
                    }
                )
                OndaTab.Search -> SearchScreen(modifier = inner, state = state, vm = vm)
                OndaTab.Library -> LibraryScreen(
                    modifier = inner,
                    state = state,
                    vm = vm,
                    onCreatePlaylist = { createPlaylist = true },
                    onCreateAlbum = { createAlbum = true },
                    onOpenAlbum = { id, name -> collection = CollectionTarget(id, name, true) },
                    onOpenPlaylist = { id, name -> collection = CollectionTarget(id, name, false) },
                    onOpenLiked = { collection = CollectionTarget(LIKED_PLAYLIST_ID, "Me gusta", false) }
                )
            }
        }

        if (showPlayer) {
            NowPlayingScreen(state = state, vm = vm, onClose = { showPlayer = false })
        }

        if (showProfile) ProfileDialog(vm = vm, onDismiss = { showProfile = false })

        collection?.let { target ->
            CollectionDialog(
                vm = vm,
                collectionId = target.id,
                name = target.name,
                isAlbum = target.isAlbum,
                onDismiss = { collection = null },
                onRename = {
                    renameTarget = target
                    collection = null
                }
            )
        }

        if (createPlaylist) {
            NameDialog(
                title = "Nueva playlist",
                label = "Nombre de la playlist",
                onDismiss = { createPlaylist = false },
                onConfirm = { name -> vm.createPlaylist(name) { createPlaylist = false } }
            )
        }

        if (createAlbum) {
            NameDialog(
                title = "Nuevo álbum",
                label = "Nombre del álbum",
                onDismiss = { createAlbum = false },
                onConfirm = { name -> vm.createAlbum(name) { createAlbum = false } }
            )
        }

        renameTarget?.let { target ->
            NameDialog(
                title = "Renombrar playlist",
                label = "Nuevo nombre",
                onDismiss = { renameTarget = null },
                onConfirm = { name ->
                    vm.renamePlaylist(target.id, name)
                    renameTarget = null
                }
            )
        }

        state.shareCode?.let { code ->
            val context = LocalContext.current
            AlertDialog(
                onDismissRequest = { vm.clearQr() },
                title = { Text("Compartir álbum o playlist") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.qr?.let { bitmap ->
                            Surface(shape = RoundedCornerShape(8.dp)) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "QR de la colección",
                                    modifier = Modifier.size(200.dp)
                                )
                            }
                            Text(
                                "Tu amigo puede escanearlo desde Buscar → Escanear QR",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text("O copia y envía este código:", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = code,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Código Onda", code))
                                    Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar código")
                                }
                            }
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, code)
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartir código Onda"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Text("Compartir código", modifier = Modifier.padding(start = 6.dp))
                    }
                },
                dismissButton = { TextButton(onClick = { vm.clearQr() }) { Text("Cerrar") } }
            )
        }

        state.updateAvailable?.let { tag ->
            AlertDialog(
                onDismissRequest = { vm.dismissUpdate() },
                title = { Text("Actualización $tag") },
                text = { Text("Se instalará sobre la versión actual sin borrar tus álbumes ni listas.") },
                confirmButton = { Button(onClick = { vm.downloadUpdate() }) { Text("Actualizar") } },
                dismissButton = { TextButton(onClick = { vm.dismissUpdate() }) { Text("Ahora no") } }
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = { Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
