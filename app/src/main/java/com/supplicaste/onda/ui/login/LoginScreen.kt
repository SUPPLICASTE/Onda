package com.supplicaste.onda.ui.login

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.supplicaste.onda.auth.AuthManager
import com.supplicaste.onda.ui.OndaViewModel
import com.supplicaste.onda.ui.theme.OndaAccent

@Composable
fun LoginScreen(
    vm: OndaViewModel,
    message: String,
    loading: Boolean,
    mode: String,
    googleAvailable: Boolean = false,
    spotifyAvailable: Boolean = false
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var wantYouTube by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val (token, code) = try {
                AuthManager(context).googleTokenAndCode(result.data)
            } catch (e: Exception) {
                null to null
            }
            if (token != null) {
                vm.signInWithGoogleToken(token, if (wantYouTube) code else null)
            }
        }
    }

    fun openSpotify() {
        val url = vm.spotifyAuthUrl()
        if (url != null) {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        } else {
            vm.showMessage("Configura ONDA_SPOTIFY_CLIENT_ID y registra ondaoauth://spotify/callback en tu app de Spotify")
        }
    }

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
            label = { Text("Correo electrónico") },
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
            onClick = { openSpotify() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = OndaAccent, contentColor = Color.Black)
        ) { Text("Continuar con Spotify") }

        Button(
            onClick = {
                wantYouTube = false
                val intent = vm.googleSignInIntent(false)
                if (intent != null) {
                    googleLauncher.launch(intent)
                } else {
                    vm.showMessage("Configura Firebase y ONDA_GOOGLE_WEB_CLIENT_ID para entrar con Google")
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Continuar con Google") }

        Button(
            onClick = {
                wantYouTube = true
                val intent = vm.googleSignInIntent(true)
                if (intent != null) {
                    googleLauncher.launch(intent)
                } else {
                    vm.showMessage("Configura Firebase, ONDA_GOOGLE_WEB_CLIENT_ID y ONDA_GOOGLE_CLIENT_SECRET para YouTube")
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Continuar con YouTube") }

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
        if (!spotifyAvailable) {
            Text(
                "Spotify listo para configurar: añade tu Client ID en local.properties o ONDA_SPOTIFY_CLIENT_ID.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        if (!googleAvailable) {
            Text(
                "Google necesita Firebase + Web Client ID.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = Color.White)
        }
    }
}
