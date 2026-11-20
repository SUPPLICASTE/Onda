package com.supplicaste.onda.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.supplicaste.onda.data.AlbumEntity
import com.supplicaste.onda.ui.OndaViewModel

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    vm: OndaViewModel,
    onCreate: () -> Unit,
    onOpen: (AlbumEntity) -> Unit
) {
    val albums by vm.albums.collectAsState(initial = emptyList())

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tus álbumes", style = MaterialTheme.typography.headlineLarge)
        Button(onClick = onCreate) { Text("Nuevo álbum") }

        if (albums.isEmpty()) {
            Text(
                "Todavía no tienes álbumes. Crea uno y añade canciones desde Buscar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(albums, key = { it.id }) { album ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpen(album) }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(album.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Pulsa para ver, compartir o eliminar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
