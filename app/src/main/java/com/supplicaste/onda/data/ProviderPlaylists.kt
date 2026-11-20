package com.supplicaste.onda.data

/** Formato común para importar playlists de proveedores externos. */
data class ProviderTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val artwork: String,
    val sourceUrl: String
)

data class ProviderPlaylist(
    val name: String,
    val tracks: List<ProviderTrack>
)
