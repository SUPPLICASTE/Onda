package com.supplicaste.onda.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import com.supplicaste.onda.ui.OndaUiState
import com.supplicaste.onda.ui.OndaViewModel
import com.supplicaste.onda.ui.components.Artwork
import com.supplicaste.onda.ui.theme.OndaAccent
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: OndaUiState,
    vm: OndaViewModel,
    onClose: () -> Unit
) {
    var showQueue by remember { mutableStateOf(false) }
    var sliderPosition by remember(state.currentSong?.id) { mutableStateOf(state.position.toFloat()) }
    if (state.currentSong == null) sliderPosition = state.position.toFloat()

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar reproductor")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("REPRODUCIENDO DESDE", style = MaterialTheme.typography.labelSmall)
                        Text(
                            if (state.djEnabled) "DJ de Onda" else "Tu música",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showQueue = !showQueue }) {
                        Icon(
                            Icons.Filled.QueueMusic,
                            contentDescription = "Cola de reproducción",
                            tint = if (showQueue) OndaAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                val song = state.currentSong
                if (song == null) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No hay nada sonando", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Busca una canción y pulsa reproducir.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Artwork(
                        url = song.artwork,
                        size = 260.dp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(Modifier.height(24.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val liked = song.id in state.likedIds
                        IconButton(onClick = { vm.toggleLike(song) }) {
                            Icon(
                                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Me gusta",
                                tint = if (liked) OndaAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = { vm.seekTo(sliderPosition.roundToLong()) },
                        valueRange = 0f..maxOf(1f, state.duration.toFloat()),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(sliderPosition.toLong()), style = MaterialTheme.typography.bodySmall)
                        Text(formatTime(state.duration), style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { vm.setShuffle(!state.shuffle) }) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = "Aleatorio",
                                tint = if (state.shuffle) OndaAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { vm.previous() }) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(34.dp))
                        }
                        IconButton(
                            onClick = { vm.togglePlay() },
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(OndaAccent)
                        ) {
                            Icon(
                                if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.playing) "Pausar" else "Reproducir",
                                tint = Color.Black,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        IconButton(onClick = { vm.next() }) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", modifier = Modifier.size(34.dp))
                        }
                        IconButton(onClick = { vm.cycleRepeat() }) {
                            Icon(
                                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                contentDescription = "Repetir",
                                tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) OndaAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { vm.djNext() }, label = { Text("DJ: siguiente") })
                        AssistChip(onClick = { vm.addNext(song) }, label = { Text("Poner siguiente") })
                    }

                    if (showQueue) {
                        Spacer(Modifier.height(12.dp))
                        Text("Cola (${state.queue.size})", style = MaterialTheme.typography.titleMedium)
                        LazyColumn(Modifier.weight(1f)) {
                            itemsIndexed(state.queue) { index, queued ->
                                val current = index == state.queueIndex
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}. ${queued.title} — ${queued.artist}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (current) OndaAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Start
                                    )
                                    IconButton(onClick = { vm.removeFromQueue(index) }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Quitar de la cola",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
