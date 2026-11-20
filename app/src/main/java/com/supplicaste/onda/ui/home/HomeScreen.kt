package com.supplicaste.onda.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.supplicaste.onda.ui.OndaUiState
import com.supplicaste.onda.ui.OndaViewModel

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    state: OndaUiState,
    vm: OndaViewModel
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Hecho para ti", style = MaterialTheme.typography.headlineLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { vm.toggleDj() },
                label = { Text(if (state.djEnabled) "DJ activado" else "Activar DJ") }
            )
            if (state.djEnabled) {
                Button(onClick = { vm.djNext() }) { Text("Siguiente canción") }
            }
        }

        state.currentSong?.let { song ->
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { vm.togglePlay() }) {
                        Text(if (state.playing) "Pausar" else "Reproducir")
                    }
                }
            }
        }

        Text(
            text = "Busca una canción y pulsa + para guardarla en uno de tus álbumes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
