package com.supplicaste.onda.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.supplicaste.onda.data.LIKED_PLAYLIST_ID
import com.supplicaste.onda.data.Song
import com.supplicaste.onda.ui.OndaViewModel

/** Acciones de la canción que suena: guardarla en álbum, playlist o Me gusta. */
@Composable
fun SongMoreButton(
    song: Song,
    liked: Boolean,
    vm: OndaViewModel,
    modifier: Modifier = Modifier
) {
    val albums by vm.albums.collectAsState(initial = emptyList())
    val playlists by vm.playlists.collectAsState(initial = emptyList())
    var expanded by remember { mutableStateOf(false) }
    var albumsOpen by remember { mutableStateOf(false) }
    var playlistsOpen by remember { mutableStateOf(false) }

    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Opciones de la canción")
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Añadir a álbum") },
                leadingIcon = { Icon(Icons.Filled.Album, contentDescription = null) },
                onClick = { albumsOpen = true; expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Añadir a playlist") },
                leadingIcon = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                onClick = { playlistsOpen = true; expanded = false }
            )
            DropdownMenuItem(
                text = { Text(if (liked) "Quitar de Me gusta" else "Añadir a Me gusta") },
                leadingIcon = {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null
                    )
                },
                onClick = { vm.toggleLike(song); expanded = false }
            )
        }

        DropdownMenu(expanded = albumsOpen, onDismissRequest = { albumsOpen = false }) {
            if (albums.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Crea un álbum en Biblioteca primero") },
                    onClick = { albumsOpen = false }
                )
            } else {
                albums.forEach { album ->
                    DropdownMenuItem(
                        text = { Text(album.name) },
                        onClick = { vm.addToAlbum(album.id, song); albumsOpen = false }
                    )
                }
            }
        }

        DropdownMenu(expanded = playlistsOpen, onDismissRequest = { playlistsOpen = false }) {
            DropdownMenuItem(
                text = { Text("Me gusta") },
                onClick = { vm.addToPlaylist(LIKED_PLAYLIST_ID, song); playlistsOpen = false }
            )
            playlists.forEach { playlist ->
                DropdownMenuItem(
                    text = { Text(playlist.name) },
                    onClick = { vm.addToPlaylist(playlist.id, song); playlistsOpen = false }
                )
            }
        }
    }
}
