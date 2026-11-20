package com.supplicaste.onda.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.google.zxing.integration.android.IntentIntegrator
import com.supplicaste.onda.ui.OndaUiState
import com.supplicaste.onda.ui.OndaViewModel
import com.supplicaste.onda.ui.components.SongRow

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    state: OndaUiState,
    vm: OndaViewModel
) {
    var query by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    val albums by vm.albums.collectAsState(initial = emptyList())
    val playlists by vm.playlists.collectAsState(initial = emptyList())
    val recents by vm.recentSearches.collectAsState(initial = emptyList())
    val activity = LocalContext.current as? ComponentActivity

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Canciones, artistas o álbumes") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (query.isNotBlank()) {
                    androidx.compose.material3.IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Limpiar")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(query) })
        )

        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { vm.search(query) }, enabled = query.isNotBlank()) { Text("Buscar") }
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            Text(
                text = if (state.results.isEmpty()) "" else "${state.results.size} resultados",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (recents.isNotEmpty() && state.results.isEmpty()) {
            Text(
                "Búsquedas recientes",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recents.take(5).forEach { recent ->
                    AssistChip(
                        onClick = { query = recent; vm.search(recent) },
                        label = { Text(recent, maxLines = 1) }
                    )
                }
            }
            TextButton(onClick = { vm.clearSearches() }) { Text("Borrar historial") }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(state.results, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    liked = song.id in state.likedIds,
                    albums = albums,
                    playlists = playlists,
                    onPlay = { vm.play(song, state.results) },
                    onLike = { vm.toggleLike(song) },
                    onAddAlbum = { vm.addToAlbum(it, song) },
                    onAddPlaylist = { vm.addToPlaylist(it, song) },
                    onPlayNext = { vm.addNext(song) }
                )
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
                modifier = Modifier.padding(top = 8.dp),
                enabled = importText.isNotBlank()
            ) { Text("Importar código") }

            Button(
                onClick = {
                    activity?.let { IntentIntegrator(it).initiateScan() }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Text("Escanear QR", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
