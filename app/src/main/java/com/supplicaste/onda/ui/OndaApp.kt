package com.supplicaste.onda.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.supplicaste.onda.data.AlbumEntity
import com.supplicaste.onda.ui.home.HomeScreen
import com.supplicaste.onda.ui.library.AlbumDialog
import com.supplicaste.onda.ui.login.LoginScreen
import com.supplicaste.onda.ui.library.LibraryScreen
import com.supplicaste.onda.ui.player.MiniPlayer
import com.supplicaste.onda.ui.search.SearchScreen

private enum class OndaTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("Inicio", Icons.Filled.Home),
    Search("Buscar", Icons.Filled.Search),
    Library("Biblioteca", Icons.Filled.LibraryMusic)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OndaApp(vm: OndaViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(OndaTab.Home) }
    var selectedAlbum by remember { mutableStateOf<AlbumEntity?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        if (state.message.isNotBlank()) {
            snackbar.showSnackbar(state.message)
            vm.clearMessage()
        }
    }

    if (state.email == null) {
        LoginScreen(vm = vm, message = state.message, loading = state.loading, mode = state.authMode)
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Onda") },
                actions = {
                    IconButton(onClick = { vm.checkUpdate() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Comprobar actualizaciones")
                    }
                    TextButton(onClick = { vm.signOut() }) { Text("Salir") }
                }
            )
        },
        bottomBar = {
            Column {
                MiniPlayer(state = state, vm = vm)
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
        when (tab) {
            OndaTab.Home -> HomeScreen(
                modifier = Modifier.padding(padding).fillMaxSize(),
                state = state,
                vm = vm
            )
            OndaTab.Search -> SearchScreen(
                modifier = Modifier.padding(padding).fillMaxSize(),
                state = state,
                vm = vm
            )
            OndaTab.Library -> LibraryScreen(
                modifier = Modifier.padding(padding).fillMaxSize(),
                vm = vm,
                onCreate = { showCreate = true },
                onOpen = { selectedAlbum = it }
            )
        }
    }

    selectedAlbum?.let { album ->
        AlbumDialog(album = album, vm = vm, onDismiss = { selectedAlbum = null })
    }

    if (showCreate) {
        CreateAlbumDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                vm.createAlbum(name) { showCreate = false }
            }
        )
    }

    state.qr?.let { bitmap ->
        AlertDialog(
            onDismissRequest = { vm.clearQr() },
            title = { Text("Compartir álbum") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(shape = RoundedCornerShape(8.dp)) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR del álbum",
                            modifier = Modifier.size(220.dp)
                        )
                    }
                    Text("Tu amigo puede escanearlo desde Buscar → Importar código")
                }
            },
            confirmButton = { TextButton(onClick = { vm.clearQr() }) { Text("Cerrar") } }
        )
    }

    state.updateAvailable?.let { tag ->
        AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text("Actualización $tag") },
            text = { Text("Se instalará sobre la versión actual sin borrar tus álbumes.") },
            confirmButton = { Button(onClick = { vm.downloadUpdate() }) { Text("Actualizar") } },
            dismissButton = { TextButton(onClick = { vm.dismissUpdate() }) { Text("Ahora no") } }
        )
    }
}

@Composable
private fun CreateAlbumDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo álbum") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del álbum") },
                singleLine = true
            )
        },
        confirmButton = { Button(onClick = { onCreate(name) }) { Text("Crear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
