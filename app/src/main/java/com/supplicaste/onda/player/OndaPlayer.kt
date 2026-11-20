package com.supplicaste.onda.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/** Puente entre la UI y el servicio de reproducción. */
@UnstableApi
object OndaPlayer {
    private var controller: MediaController? = null

    fun connect(context: Context, onReady: () -> Unit) {
        if (controller != null) {
            onReady()
            return
        }
        val token = SessionToken(context, ComponentName(context, OndaPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                controller = runCatching { future.get() }.getOrNull()
                onReady()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun get(): MediaController? = controller

    fun disconnect() {
        controller?.release()
        controller = null
    }
}
