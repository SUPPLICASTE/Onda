package com.supplicaste.onda.ui.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.supplicaste.onda.auth.AuthManager
import com.supplicaste.onda.ui.OndaViewModel

@Composable
fun LoginScreen(
    vm: OndaViewModel,
    message: String,
    loading: Boolean,
    mode: String,
    googleAvailable: Boolean = false
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val token = try {
                AuthManager(context).googleTokenFrom(result.data)
            } catch (e: Exception) {
                null
            }
            if (token != null) vm.signInWithGoogleToken(token)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Onda", style = MaterialTheme.typography.headlineLarge)
        Text("Tu música, sin ruido", style = MaterialTheme.typography.bodyMedium)
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
            onClick = {
                vm.googleSignInIntent()?.let { googleLauncher.launch(it) }
            },
            enabled = !loading && googleAvailable,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Continuar con Google") }

        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Spotify · Próximamente") }

        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) { Text("YouTube · Próximamente") }

        Spacer(Modifier.height(12.dp))

        Text(
            text = when {
                !googleAvailable && mode == "Local" ->
                    "Modo local: el correo se guarda solo en este dispositivo. " +
                        "Configura Firebase y el Client ID de Google para activar el acceso real."
                !googleAvailable ->
                    "Configura ONDA_GOOGLE_WEB_CLIENT_ID para activar el acceso con Google."
                else -> "Registro verificado con Firebase"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
