package com.supplicaste.onda

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.supplicaste.onda.ui.OndaApp
import com.supplicaste.onda.ui.OndaBus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
        setContent { OndaApp() }
        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** Callback OAuth de Spotify: ondaoauth://spotify/callback?code=...&state=... */
    private fun handleIncoming(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "ondaoauth" || data.host != "spotify") return
        val code = data.getQueryParameter("code") ?: return
        val state = data.getQueryParameter("state").orEmpty()
        OndaBus.spotifyCode.tryEmit("$code|$state")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        IntentIntegrator.parseActivityResult(requestCode, resultCode, data)?.contents?.let { code ->
            OndaBus.imported.tryEmit(code)
        }
    }
}
