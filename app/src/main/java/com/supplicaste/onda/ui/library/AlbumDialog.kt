package com.supplicaste.onda.ui.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.supplicaste.onda.data.AlbumEntity
import com.supplicaste.onda.data.toSong
import com.supplicaste.onda.ui.OndaViewModel

@Composable
fun AlbumDialog(album: AlbumEntity, vm: OndaViewModel, onDismiss: () -> Unit) {
    val songs by vm.songsOf(album.id).collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(album.name) },
        text = {
            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(songs, key = { it.id }) { song ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(song.title, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.play(song.toSong()) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
                        }
                        IconButton(onClick = { vm.removeFromAlbum(album.id, song.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Quitar del álbum")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.shareAlbum(album) }) { Text("Compartir QR") }
        },
        dismissButton = {
            TextButton(onClick = {
                vm.deleteAlbum(album.id)
                onDismiss()
            }) { Text("Eliminar álbum") }
        }
    )
}
