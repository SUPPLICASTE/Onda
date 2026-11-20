package com.supplicaste.onda.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Onda/1.0"

object OndaPlayer {
    private var player: ExoPlayer? = null

    fun get(context: Context): ExoPlayer {
        if (player == null) {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
            val dataSourceFactory = DefaultDataSource.Factory(context.applicationContext, httpFactory)
            player = ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
        }
        return player!!
    }

    fun play(context: Context, url: String) {
        val p = get(context)
        p.stop()
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
    }

    fun isPlaying(): Boolean = player?.isPlaying == true

    fun toggle() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun release() {
        player?.release()
        player = null
    }
}
