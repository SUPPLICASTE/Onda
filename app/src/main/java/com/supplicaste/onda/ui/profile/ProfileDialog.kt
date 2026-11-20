package com.supplicaste.onda.ui.profile

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.supplicaste.onda.BuildConfig
import com.supplicaste.onda.ui.OndaViewModel
import com.supplicaste.onda.ui.login.SpotifyLoginSheet
import com.supplicaste.onda.ui.theme.OndaAccent

@Composable
fun ProfileDialog(vm: OndaViewModel, onDismiss: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var clientId by remember { mutableStateOf("") }
    var showSpotifyLogin by remember { mutableStateOf(false) }

    Box {
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

                Text("Spotify", style = MaterialTheme.typography.titleMedium)
                if (state.spotifyLinked) {
                    Text(
                        "Vinculado como ${state.spotifyEmail.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { vm.refreshSpotify() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Importar playlists de nuevo") }
                    OutlinedButton(
                        onClick = { vm.unlinkSpotify() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Desvincular Spotify") }
                } else {
                    Button(
                        onClick = { showSpotifyLogin = true },
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OndaAccent,
                            contentColor = Color.Black
                        )
                    ) { Text("Iniciar sesión con Spotify") }
                    Text(
                        "Importa tus playlists con carátula. El audio se busca en YouTube al reproducir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            val url = vm.spotifyAuthUrl()
                            if (url != null) {
                                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                            } else {
                                vm.showMessage("Guarda tu Client ID aquí y registra ondaoauth://spotify/callback en Spotify")
                            }
                        },
                        enabled = !state.loading
                    ) { Text("Usar OAuth con Client ID") }
                }

                Text("Client ID de Spotify", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Pégalo desde Spotify Developer Dashboard") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { vm.setSpotifyClientId(clientId) },
                    enabled = clientId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar Client ID") }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
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
        if (showSpotifyLogin) {
            SpotifyLoginSheet(
                onDismiss = { showSpotifyLogin = false },
                onCookiesCaptured = { spDc, spKey ->
                    showSpotifyLogin = false
                    vm.connectSpotifyWithCookies(spDc, spKey)
                }
            )
        }
    }
}
