package com.supplicaste.onda.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.supplicaste.onda.data.LIKED_PLAYLIST_ID
import com.supplicaste.onda.data.SongEntity
import com.supplicaste.onda.data.toSong
import com.supplicaste.onda.ui.OndaViewModel
import com.supplicaste.onda.ui.theme.OndaAccent

@Composable
fun CollectionDialog(
    vm: OndaViewModel,
    collectionId: Long,
    name: String,
    isAlbum: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit
) {
    val entities by (if (isAlbum) vm.songsOfAlbum(collectionId) else vm.songsOfPlaylist(collectionId))
        .collectAsState(initial = emptyList())
    val state by vm.state.collectAsState()
    val songs = entities.map { it.toSong() }
    val isLiked = !isAlbum && collectionId == LIKED_PLAYLIST_ID
    val canEdit = !isLiked

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        songs.firstOrNull()?.let { vm.play(it, songs) }
                    }, enabled = songs.isNotEmpty()) { Text("Reproducir todo") }

                    TextButton(onClick = {
                        vm.setShuffle(true)
                        songs.randomOrNull()?.let { vm.play(it, songs.shuffled()) }
                    }, enabled = songs.isNotEmpty()) { Text("Aleatorio") }

                    if (canEdit) TextButton(onClick = onRename) { Text("Renombrar") }
                }

                if (songs.isEmpty()) {
                    Text(
                        "Sin canciones. Añádelas desde Buscar con el botón + de cada canción.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(songs, key = { it.id }) { song ->
                            SongLine(
                                song = song,
                                liked = song.id in state.likedIds,
                                onPlay = { vm.play(song, songs) },
                                onLike = { vm.toggleLike(song) },
                                onRemove = {
                                    if (isAlbum) vm.removeFromAlbum(collectionId, song.id)
                                    else vm.removeFromPlaylist(collectionId, song.id)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.shareList(name, songs) }) { Text("Compartir QR") }
        },
        dismissButton = {
            Row {
                if (canEdit) {
                    TextButton(onClick = {
                        if (isAlbum) vm.deleteAlbum(collectionId) else vm.deletePlaylist(collectionId)
                        onDismiss()
                    }) { Text("Eliminar") }
                }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    )
}

@Composable
private fun SongLine(
    song: com.supplicaste.onda.data.Song,
    liked: Boolean,
    onPlay: () -> Unit,
    onLike: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        IconButton(onClick = onPlay) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
        }
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onLike) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Me gusta",
                tint = if (liked) OndaAccent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Quitar")
        }
    }
}
