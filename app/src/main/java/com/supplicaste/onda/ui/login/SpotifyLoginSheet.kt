package com.supplicaste.onda.ui.login

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.supplicaste.onda.auth.SpotifyWebAuth

private const val LOGIN_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

/**
 * Inicia sesión con la cuenta de Spotify dentro de la app y devuelve las
 * cookies `sp_dc`/`sp_key`, igual que hace ArchiveTune.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginSheet(
    onDismiss: () -> Unit,
    onCookiesCaptured: (spDc: String, spKey: String) -> Unit,
) {
    val captured = remember { mutableStateOf(false) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    val rootWebView = remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val webView = activeWebView
        if (webView?.canGoBack() == true) webView.goBack() else onDismiss()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
                Text("Iniciar sesión con Spotify", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Inicia sesión en la ventana de Spotify. Onda solo guarda las cookies de sesión en este dispositivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                AndroidView(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp)),
                    factory = { context ->
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        val container = FrameLayout(context)

                        fun configure(view: WebView) {
                            view.settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(true)
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                userAgentString = LOGIN_USER_AGENT
                            }
                            cookieManager.setAcceptThirdPartyCookies(view, true)
                        }

                        fun captureCookies(url: String?): Boolean {
                            if (captured.value) return true
                            val cookies = linkedMapOf<String, String>()
                            val origins =
                                linkedSetOf(
                                    "https://open.spotify.com",
                                    "https://accounts.spotify.com",
                                    "https://spotify.com",
                                )
                            url?.let { Uri.parse(it) }?.let { uri ->
                                val host = uri.host?.lowercase().orEmpty()
                                if (host == "spotify.com" || host.endsWith(".spotify.com")) {
                                    origins.add("${uri.scheme ?: "https"}://$host")
                                }
                            }
                            cookieManager.flush()
                            origins.forEach { origin ->
                                cookieManager
                                    .getCookie(origin)
                                    ?.split(";")
                                    ?.forEach { part ->
                                        val key = part.substringBefore("=").trim()
                                        val value = part.substringAfter("=", "").trim()
                                        if (key.isNotBlank() && value.isNotBlank()) cookies[key] = value
                                    }
                            }
                            val spDc = cookies["sp_dc"].orEmpty()
                            if (spDc.isBlank()) return false
                            captured.value = true
                            onCookiesCaptured(spDc, cookies["sp_key"].orEmpty())
                            return true
                        }

                        fun webViewClient(): WebViewClient =
                            object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean = captureCookies(request.url?.toString())

                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    url: String?,
                                ): Boolean = captureCookies(url)

                                override fun onPageStarted(
                                    view: WebView,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    captureCookies(url)
                                }

                                override fun onPageFinished(
                                    view: WebView,
                                    url: String?,
                                ) {
                                    captureCookies(url)
                                    view.postDelayed({ captureCookies(view.url) }, 900L)
                                }
                            }

                        fun webChromeClient(): WebChromeClient =
                            object : WebChromeClient() {
                                override fun onCreateWindow(
                                    view: WebView,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message,
                                ): Boolean {
                                    val popup =
                                        WebView(context).apply {
                                            configure(this)
                                            webViewClient = webViewClient()
                                            webChromeClient = webChromeClient()
                                        }
                                    container.addView(
                                        popup,
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        ),
                                    )
                                    popup.bringToFront()
                                    activeWebView = popup
                                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                    transport.webView = popup
                                    resultMsg.sendToTarget()
                                    return true
                                }

                                override fun onCloseWindow(window: WebView) {
                                    window.stopLoading()
                                    (window.parent as? ViewGroup)?.removeView(window)
                                    window.destroy()
                                    activeWebView = rootWebView.value
                                }
                            }

                        val main =
                            WebView(context).apply {
                                configure(this)
                                webViewClient = webViewClient()
                                webChromeClient = webChromeClient()
                            }
                        container.addView(
                            main,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        rootWebView.value = main
                        activeWebView = main

                        // Esperar a que se borren las cookies antes de cargar;
                        // si se hace en paralelo se puede perder la sesión nueva.
                        cookieManager.removeAllCookies {
                            cookieManager.flush()
                            main.post {
                                main.clearCache(true)
                                main.clearHistory()
                                main.clearFormData()
                                main.loadUrl(SpotifyWebAuth.LOGIN_URL)
                            }
                        }
                        container
                    },
                    onRelease = { container ->
                        for (index in container.childCount - 1 downTo 0) {
                            val view = container.getChildAt(index) as? WebView ?: continue
                            view.stopLoading()
                            view.loadUrl("about:blank")
                            (view.parent as? ViewGroup)?.removeView(view)
                            view.destroy()
                        }
                    },
                )

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancelar")
                }
            }
        }
    }
}
