package com.supplicaste.onda.ui.library

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.supplicaste.onda.ui.OndaUiState
import com.supplicaste.onda.ui.OndaViewModel
import com.supplicaste.onda.ui.theme.OndaAccent
import com.supplicaste.onda.ui.theme.OndaSurface

private enum class LibraryFilter { Todo, Playlists, Albums, Liked }

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    state: OndaUiState,
    vm: OndaViewModel,
    onCreatePlaylist: () -> Unit,
    onCreateAlbum: () -> Unit,
    onOpenAlbum: (Long, String) -> Unit,
    onOpenPlaylist: (Long, String) -> Unit,
    onOpenLiked: () -> Unit
) {
    val albums by vm.albums.collectAsState(initial = emptyList())
    val playlists by vm.playlists.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf(LibraryFilter.Todo) }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Text("Tu biblioteca", style = MaterialTheme.typography.headlineLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            Button(onClick = onCreatePlaylist) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Playlist", modifier = Modifier.padding(start = 4.dp))
            }
            Button(onClick = onCreateAlbum) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Álbum", modifier = Modifier.padding(start = 4.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            LibraryFilter.entries.forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { filter = item },
                    label = { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filter == LibraryFilter.Todo || filter == LibraryFilter.Liked) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenLiked,
                        colors = CardDefaults.cardColors(containerColor = OndaSurface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = OndaAccent,
                                modifier = Modifier.size(40.dp)
                            )
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text("Me gusta", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${state.stats.liked} canciones guardadas",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (filter == LibraryFilter.Todo || filter == LibraryFilter.Playlists) {
                items(playlists, key = { "p${it.id}" }) { playlist ->
                    CollectionCard(
                        icon = Icons.Filled.QueueMusic,
                        title = playlist.name,
                        subtitle = "Playlist",
                        onClick = { onOpenPlaylist(playlist.id, playlist.name) }
                    )
                }
            }

            if (filter == LibraryFilter.Todo || filter == LibraryFilter.Albums) {
                items(albums, key = { "a${it.id}" }) { album ->
                    CollectionCard(
                        icon = Icons.Filled.Album,
                        title = album.name,
                        subtitle = "Álbum",
                        onClick = { onOpenAlbum(album.id, album.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = OndaSurface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = OndaAccent)
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
