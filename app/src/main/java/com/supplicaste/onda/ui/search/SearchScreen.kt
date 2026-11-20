package com.supplicaste.onda.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.integration.android.IntentIntegrator
import com.supplicaste.onda.data.Song
import com.supplicaste.onda.ui.OndaUiState
import com.supplicaste.onda.ui.OndaViewModel
import com.supplicaste.onda.ui.components.Artwork
import com.supplicaste.onda.ui.components.PlayOverlay
import com.supplicaste.onda.ui.components.SongRow
import com.supplicaste.onda.ui.theme.OndaSurface

private enum class SearchFilter { Todo, Canciones, Artistas }

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    state: OndaUiState,
    vm: OndaViewModel
) {
    var query by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(SearchFilter.Todo) }
    val albums by vm.albums.collectAsState(initial = emptyList())
    val playlists by vm.playlists.collectAsState(initial = emptyList())
    val recents by vm.recentSearches.collectAsState(initial = emptyList())
    val activity = LocalContext.current as? ComponentActivity
    val artists = remember(state.results) { state.results.map { it.artist }.distinct().filter { it.isNotBlank() } }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("¿Qué quieres escuchar?") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Limpiar")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(query) })
        )

        Spacer(Modifier.size(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchFilter.entries.forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { filter = item },
                    label = { Text(item.name) }
                )
            }
            if (state.loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (recents.isNotEmpty()) {
                item { SectionLabel("Búsquedas recientes") }
                items(recents, key = { it }) { recent ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = { query = recent; vm.search(recent) },
                            label = { Text(recent, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { vm.deleteSearch(recent) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda")
                        }
                    }
                }
                item { TextButton(onClick = { vm.clearSearches() }) { Text("Borrar historial") } }
            }

            if (state.results.isEmpty()) {
                item {
                    Text(
                        "Busca una canción, un artista o un grupo. Toca un resultado para reproducirlo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else when (filter) {
                SearchFilter.Todo -> {
                    item {
                        SectionLabel("Mejor resultado")
                        TopResult(
                            song = state.results.first(),
                            liked = state.results.first().id in state.likedIds,
                            onPlay = { vm.play(state.results.first(), state.results) },
                            onLike = { vm.toggleLike(state.results.first()) }
                        )
                    }
                    item { SectionLabel("Canciones") }
                    items(state.results, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            liked = song.id in state.likedIds,
                            albums = albums,
                            playlists = playlists,
                            selected = song.id == state.currentSong?.id,
                            onPlay = { vm.play(song, state.results) },
                            onLike = { vm.toggleLike(song) },
                            onAddAlbum = { vm.addToAlbum(it, song) },
                            onAddPlaylist = { vm.addToPlaylist(it, song) },
                            onPlayNext = { vm.addNext(song) }
                        )
                    }
                }
                SearchFilter.Canciones -> {
                    items(state.results, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            liked = song.id in state.likedIds,
                            albums = albums,
                            playlists = playlists,
                            selected = song.id == state.currentSong?.id,
                            onPlay = { vm.play(song, state.results) },
                            onLike = { vm.toggleLike(song) },
                            onAddAlbum = { vm.addToAlbum(it, song) },
                            onAddPlaylist = { vm.addToPlaylist(it, song) },
                            onPlayNext = { vm.addNext(song) }
                        )
                    }
                }
                SearchFilter.Artistas -> {
                    items(artists, key = { it }) { artist ->
                        ArtistCard(name = artist, onClick = { query = artist; vm.search(artist) })
                    }
                }
            }
        }

        if (!showImport) {
            TextButton(onClick = { showImport = true }) { Text("Importar un código compartido") }
        } else {
            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                placeholder = { Text("Pega aquí el código de tu amigo") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.importShared(importText.trim()); importText = "" },
                    enabled = importText.isNotBlank()
                ) { Text("Importar") }
                Button(onClick = { activity?.let { IntentIntegrator(it).initiateScan() } }) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                    Text("Escanear QR", modifier = Modifier.padding(start = 6.dp))
                }
                TextButton(onClick = { showImport = false }) { Text("Cerrar") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun TopResult(song: Song, liked: Boolean, onPlay: () -> Unit, onLike: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onPlay() },
        colors = CardDefaults.cardColors(containerColor = OndaSurface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Artwork(song.artwork, 88.dp)
                PlayOverlay(onClick = onPlay, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.size(6.dp))
                AssistChip(onClick = onLike, label = { Text(if (liked) "En Me gusta" else "Me gusta") })
            }
        }
    }
}

@Composable
private fun ArtistCard(name: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = OndaSurface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(14.dp))
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
