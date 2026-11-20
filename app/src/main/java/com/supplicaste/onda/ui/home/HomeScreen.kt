package com.supplicaste.onda.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.supplicaste.onda.data.Song
import com.supplicaste.onda.ui.OndaUiState
import com.supplicaste.onda.ui.OndaViewModel
import com.supplicaste.onda.ui.components.PlayOverlay
import com.supplicaste.onda.ui.theme.OndaAccent
import com.supplicaste.onda.ui.theme.OndaSurface
import java.util.Calendar

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
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Buenos días"
        in 12..19 -> "Buenas tardes"
        else -> "Buenas noches"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Text(greeting, style = MaterialTheme.typography.headlineMedium)
            Text(
                "Hecho para ti",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    MixCard(
                        title = "DJ de Onda",
                        subtitle = if (state.djEnabled) "Activado" else "Descubre tu próxima canción",
                        icon = Icons.Filled.AutoAwesome,
                        background = OndaAccent,
                        contentColor = Color.Black,
                        onClick = { vm.toggleDj() }
                    )
                }
                item {
                    MixCard(
                        title = "Me gusta",
                        subtitle = "${state.stats.liked} canciones",
                        icon = Icons.Filled.Favorite,
                        background = OndaSurface,
                        contentColor = OndaAccent,
                        onClick = onOpenLiked
                    )
                }
                item {
                    MixCard(
                        title = "Tu biblioteca",
                        subtitle = "${albums.size} álbumes · ${playlists.size} listas",
                        icon = Icons.Filled.LibraryMusic,
                        background = OndaSurface,
                        contentColor = OndaAccent,
                        onClick = {}
                    )
                }
            }
        }

        if (state.djEnabled) {
            item {
                Button(onClick = { vm.djNext() }, modifier = Modifier.padding(top = 10.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(" Pedir siguiente canción", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        item {
            Text(
                "Escuchado recientemente",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            if (state.recent.isEmpty()) {
                Text(
                    "Toca un resultado de búsqueda para empezar a escuchar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TileGrid(state.recent.take(6)) { song -> vm.play(song, state.recent) }
            }
        }

        if (state.topArtists.isNotEmpty()) {
            item {
                Text("Tus artistas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.topArtists, key = { it.artist }) { artist ->
                        AssistChip(onClick = { onSearchArtist(artist.artist) }, label = { Text(artist.artist) })
                    }
                }
            }
        }

        item {
            Text(
                "Tu biblioteca",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            val tiles = playlists.map { it.id to it.name } + albums.map { it.id to it.name }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (id, name) ->
                            LibraryTile(
                                name = name,
                                onClick = {
                                    if (playlists.any { it.id == id }) onOpenPlaylist(id, name) else onOpenAlbum(id, name)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MixCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.width(170.dp).height(96.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = contentColor, maxLines = 1)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TileGrid(songs: List<Song>, onPlay: (Song) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        songs.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { song ->
                    SongTile(song = song, onClick = { onPlay(song) }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SongTile(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = OndaSurface)) {
        Box {
            AsyncImage(
                model = song.artwork.ifBlank { null },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            )
            PlayOverlay(onClick = onClick, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        Column(Modifier.padding(10.dp)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibraryTile(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = OndaSurface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = OndaAccent)
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
