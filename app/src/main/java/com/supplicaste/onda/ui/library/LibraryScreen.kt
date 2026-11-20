package com.supplicaste.onda.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.supplicaste.onda.ui.OndaUiState
import com.supplicaste.onda.ui.OndaViewModel
import com.supplicaste.onda.ui.theme.OndaAccent
import com.supplicaste.onda.ui.theme.OndaAccentDim
import com.supplicaste.onda.ui.theme.OndaSurface

private enum class LibraryFilter(val label: String) {
    Todo("Todo"),
    Playlists("Playlists"),
    Albums("Álbumes"),
    Liked("Me gusta")
}

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
    var addMenuOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Tu biblioteca",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { addMenuOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Crear playlist o álbum")
                }
                DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Nueva playlist") },
                        leadingIcon = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                        onClick = { onCreatePlaylist(); addMenuOpen = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Nuevo álbum") },
                        leadingIcon = { Icon(Icons.Filled.Album, contentDescription = null) },
                        onClick = { onCreateAlbum(); addMenuOpen = false }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryFilter.entries.forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { filter = item },
                    label = { Text(item.label) }
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            if (filter == LibraryFilter.Todo || filter == LibraryFilter.Liked) {
                item(span = { GridItemSpan(2) }) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                        onClick = onOpenLiked,
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(92.dp)
                                .background(Brush.linearGradient(listOf(OndaAccent, OndaAccentDim)))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Me gusta",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.Black
                                )
                                Text(
                                    "${state.stats.liked} canciones guardadas",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Black.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }

            if (filter == LibraryFilter.Todo || filter == LibraryFilter.Playlists) {
                items(playlists, key = { "p${it.id}" }) { playlist ->
                    CollectionTile(
                        vm = vm,
                        collectionId = playlist.id,
                        isAlbum = false,
                        name = playlist.name,
                        subtitle = "Playlist",
                        onClick = { onOpenPlaylist(playlist.id, playlist.name) }
                    )
                }
            }

            if (filter == LibraryFilter.Todo || filter == LibraryFilter.Albums) {
                items(albums, key = { "a${it.id}" }) { album ->
                    CollectionTile(
                        vm = vm,
                        collectionId = album.id,
                        isAlbum = true,
                        name = album.name,
                        subtitle = "Álbum",
                        onClick = { onOpenAlbum(album.id, album.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionTile(
    vm: OndaViewModel,
    collectionId: Long,
    isAlbum: Boolean,
    name: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val songs by (if (isAlbum) vm.songsOfAlbum(collectionId) else vm.songsOfPlaylist(collectionId))
        .collectAsState(initial = emptyList())
    val cover = songs.firstOrNull()?.artwork.orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = OndaSurface)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = cover.ifBlank { null },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (cover.isBlank()) {
                Icon(
                    if (isAlbum) Icons.Filled.Album else Icons.Filled.QueueMusic,
                    contentDescription = null,
                    tint = OndaAccent,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
