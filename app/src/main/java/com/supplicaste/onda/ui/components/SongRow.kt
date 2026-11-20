package com.supplicaste.onda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.supplicaste.onda.data.AlbumEntity
import com.supplicaste.onda.data.PlaylistEntity
import com.supplicaste.onda.data.Song
import com.supplicaste.onda.ui.theme.OndaAccent

@Composable
fun SongRow(
    song: Song,
    liked: Boolean,
    albums: List<AlbumEntity>,
    playlists: List<PlaylistEntity>,
    onPlay: () -> Unit,
    onLike: () -> Unit,
    onAddAlbum: (Long) -> Unit,
    onAddPlaylist: (Long) -> Unit,
    onPlayNext: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var albumsOpen by remember { mutableStateOf(false) }
    var playlistsOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Artwork(song.artwork, 48.dp)
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) OndaAccent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onLike) {
            Icon(
                imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (liked) "Quitar de Me gusta" else "Añadir a Me gusta",
                tint = if (liked) OndaAccent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Reproducir") },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                    onClick = { onPlay(); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Reproducir siguiente") },
                    leadingIcon = { Icon(Icons.Filled.QueueMusic, null) },
                    onClick = { onPlayNext(); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text(if (liked) "Quitar de Me gusta" else "Añadir a Me gusta") },
                    leadingIcon = {
                        Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null)
                    },
                    onClick = { onLike(); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Añadir a álbum") },
                    leadingIcon = { Icon(Icons.Filled.Add, null) },
                    onClick = { albumsOpen = true; expanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Añadir a playlist") },
                    leadingIcon = { Icon(Icons.Filled.Add, null) },
                    onClick = { playlistsOpen = true; expanded = false }
                )
            }

            DropdownMenu(expanded = albumsOpen, onDismissRequest = { albumsOpen = false }) {
                if (albums.isEmpty()) {
                    DropdownMenuItem(text = { Text("Crea un álbum primero") }, onClick = { albumsOpen = false })
                } else {
                    albums.forEach { album ->
                        DropdownMenuItem(
                            text = { Text(album.name) },
                            onClick = { onAddAlbum(album.id); albumsOpen = false }
                        )
                    }
                }
            }

            DropdownMenu(expanded = playlistsOpen, onDismissRequest = { playlistsOpen = false }) {
                playlists.forEach { playlist ->
                    DropdownMenuItem(
                        text = { Text(playlist.name) },
                        onClick = { onAddPlaylist(playlist.id); playlistsOpen = false }
                    )
                }
            }
        }
    }
}

@Composable
fun Artwork(url: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url.ifBlank { null },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (url.isBlank()) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PlayOverlay(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(OndaAccent)
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir", tint = Color.Black)
    }
}
