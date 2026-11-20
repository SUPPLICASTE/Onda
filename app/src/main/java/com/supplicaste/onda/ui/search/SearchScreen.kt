package com.supplicaste.onda.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import com.google.zxing.integration.android.IntentIntegrator
import androidx.compose.ui.unit.dp
import com.supplicaste.onda.data.AlbumEntity
import com.supplicaste.onda.data.Song
import com.supplicaste.onda.ui.OndaUiState
import com.supplicaste.onda.ui.OndaViewModel

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    state: OndaUiState,
    vm: OndaViewModel
) {
    var query by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    val albums by vm.albums.collectAsState(initial = emptyList())
    val activity = LocalContext.current as? ComponentActivity

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Canciones, artistas o álbumes") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { vm.search(query) }) { Text("Buscar") }
            if (state.loading) CircularProgressIndicator(Modifier.size(24.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.results, key = { it.id }) { song ->
                SongRow(song, albums, vm)
            }
        }

        OutlinedTextField(
            value = importText,
            onValueChange = { importText = it },
            label = { Text("Código compartido por un amigo") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    vm.importShared(importText.trim())
                    importText = ""
                },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("Importar código") }

            Button(
                onClick = {
                    activity?.let {
                        IntentIntegrator(it).initiateScan()
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("Escanear QR") }
        }
    }
}

@Composable
private fun SongRow(song: Song, albums: List<AlbumEntity>, vm: OndaViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        IconButton(onClick = { vm.play(song) }) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
        }

        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir a álbum")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (albums.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Crea un álbum primero") },
                        onClick = { expanded = false }
                    )
                } else {
                    albums.forEach { album ->
                        DropdownMenuItem(
                            text = { Text(album.name) },
                            onClick = {
                                vm.addToAlbum(album.id, song)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
