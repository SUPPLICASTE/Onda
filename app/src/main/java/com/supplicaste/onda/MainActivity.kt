package com.supplicaste.onda

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.zxing.integration.android.IntentIntegrator
import com.supplicaste.onda.ui.OndaApp
import com.supplicaste.onda.ui.OndaBus
import com.supplicaste.onda.ui.theme.OndaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OndaTheme {
                OndaApp()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        IntentIntegrator.parseActivityResult(requestCode, resultCode, data)?.contents?.let { code ->
            OndaBus.imported.tryEmit(code)
        }
    }
}
