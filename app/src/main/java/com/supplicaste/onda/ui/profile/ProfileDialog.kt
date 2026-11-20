package com.supplicaste.onda.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.supplicaste.onda.BuildConfig
import com.supplicaste.onda.ui.OndaViewModel

@Composable
fun ProfileDialog(vm: OndaViewModel, onDismiss: () -> Unit) {
    val state by vm.state.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tu perfil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.email.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(
                    "Cuenta: ${state.authMode} · Versión ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Me gusta: ${state.stats.liked}")
                    Text("Álbumes: ${state.stats.albums}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Playlists: ${state.stats.playlists}")
                    Text("Reproducciones: ${state.stats.plays}")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Tema oscuro", modifier = Modifier.weight(1f))
                    Switch(checked = state.darkTheme, onCheckedChange = { vm.toggleTheme() })
                }

                TextButton(onClick = { vm.checkUpdate() }) {
                    Text("Comprobar actualizaciones")
                }
                Text(
                    "Sin anuncios. La música se obtiene de fuentes públicas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        dismissButton = { TextButton(onClick = { vm.signOut() }) { Text("Cerrar sesión") } }
    )
}
