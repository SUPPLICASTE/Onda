package com.supplicaste.onda.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.supplicaste.onda.data.Song
import com.supplicaste.onda.ui.OndaUiState
import com.supplicaste.onda.ui.OndaViewModel
import com.supplicaste.onda.ui.components.Artwork
import com.supplicaste.onda.ui.components.PlayOverlay
import com.supplicaste.onda.ui.theme.OndaAccent
import com.supplicaste.onda.ui.theme.OndaSurface

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    state: OndaUiState,
    vm: OndaViewModel,
    onOpenAlbum: (Long, String) -> Unit,
    onOpenPlaylist: (Long, String) -> Unit,
    onOpenLiked: () -> Unit,
    onSearchArtist: (String) -> Unit
) {
    val albums by vm.albums.collectAsState(initial = emptyList())
    val playlists by vm.playlists.collectAsState(initial = emptyList())

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = OndaAccent)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("DJ de Onda", style = MaterialTheme.typography.headlineSmall, color = androidx.compose.ui.graphics.Color.Black)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Elige la siguiente canción según lo que más escuchas y lo que te gusta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.toggleDj() }) {
                            Text(if (state.djEnabled) "DJ activado" else "Activar DJ")
                        }
                        if (state.djEnabled) {
                            Button(onClick = { vm.djNext() }) { Text("Siguiente canción") }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Recientemente reproducido") }
        item {
            if (state.recent.isEmpty()) {
                EmptyHint("Aún no hay reproducciones. Busca una canción para empezar.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.recent, key = { it.id + "recent" }) { song ->
                        MediaCard(
                            title = song.title,
                            subtitle = song.artist,
                            artwork = song.artwork,
                            onClick = { vm.play(song, state.recent) },
                            onPlay = { vm.play(song, state.recent) }
                        )
                    }
                }
            }
        }

        if (state.topArtists.isNotEmpty()) {
            item { SectionTitle("Tus artistas") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.topArtists, key = { it.artist }) { artist ->
                        AssistChip(
                            onClick = { onSearchArtist(artist.artist) },
                            label = { Text(artist.artist) },
                            leadingIcon = {
                                Icon(Icons.Filled.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }
        }

        item { SectionTitle("Tu biblioteca") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Card(
                        modifier = Modifier.width(150.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = OndaSurface)
                    ) {
                        Column(
                            Modifier.padding(12.dp).height(170.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = OndaAccent,
                                modifier = Modifier.size(36.dp)
                            )
                            Column {
                                Text("Me gusta", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${state.stats.liked} canciones",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                items(albums, key = { "album${it.id}" }) { album ->
                    MediaCard(
                        title = album.name,
                        subtitle = "Álbum",
                        artwork = "",
                        onClick = { onOpenAlbum(album.id, album.name) },
                        onPlay = null
                    )
                }
                items(playlists, key = { "playlist${it.id}" }) { playlist ->
                    MediaCard(
                        title = playlist.name,
                        subtitle = "Playlist",
                        artwork = "",
                        onClick = { onOpenPlaylist(playlist.id, playlist.name) },
                        onPlay = null
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun MediaCard(
    title: String,
    subtitle: String,
    artwork: String,
    onClick: () -> Unit,
    onPlay: (() -> Unit)?
) {
    Card(
        modifier = Modifier.width(150.dp),
        onClick = onClick,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = OndaSurface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Box {
                Artwork(artwork, 126.dp)
                if (onPlay != null) {
                    PlayOverlay(onClick = onPlay, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
