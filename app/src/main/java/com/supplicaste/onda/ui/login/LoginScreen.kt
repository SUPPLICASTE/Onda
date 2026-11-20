package com.supplicaste.onda.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.supplicaste.onda.ui.OndaViewModel
import com.supplicaste.onda.ui.theme.OndaAccent

@Composable
fun LoginScreen(
    vm: OndaViewModel,
    message: String,
    loading: Boolean,
    mode: String,
    spotifyAvailable: Boolean = false
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showSpotifyLogin by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Onda", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Text(
            "Tu música, sin ruido",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Gmail o correo electrónico") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { vm.signIn(email, password) },
            enabled = !loading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Entrar") }

        TextButton(onClick = { vm.signUp(email, password) }) { Text("Crear cuenta") }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { showSpotifyLogin = true },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = OndaAccent, contentColor = Color.Black)
        ) { Text("Continuar con Spotify") }

        Spacer(Modifier.height(12.dp))

        Text(
            text = when {
                mode == "Local" ->
                    "Modo local: el correo se guarda solo en este dispositivo. " +
                        "Con Spotify puedes importar tus playlists y vincular la cuenta después desde Tu perfil."
                else -> "Registro verificado con Firebase · $mode"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            if (spotifyAvailable) {
                "Si ya tienes un Client ID de Spotify, puedes usar OAuth desde Tu perfil."
            } else {
                "No necesitas Client ID: inicia sesión con tu cuenta de Spotify en la ventana que se abre."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = Color.White)
        }
    }

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
