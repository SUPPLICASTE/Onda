package com.supplicaste.onda.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class SpotifyProfile(
    val id: String,
    val email: String,
    val displayName: String
)

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val artwork: String
)

data class SpotifyPlaylist(
    val name: String,
    val tracks: List<SpotifyTrack>
)

/** Lee el perfil y las playlists del usuario desde la Web API de Spotify. */
class SpotifyImporter {
    private val client = OkHttpClient()

    private suspend fun getJson(url: String, token: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                runCatching { JSONObject(body) }.getOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun profile(token: String): SpotifyProfile? = withContext(Dispatchers.IO) {
        val json = getJson("https://api.spotify.com/v1/me", token) ?: return@withContext null
        SpotifyProfile(
            id = json.optString("id"),
            email = json.optString("email"),
            displayName = json.optString("display_name").ifBlank { json.optString("id") }
        )
    }

    suspend fun playlists(token: String): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SpotifyPlaylist>()
        var next: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
        while (next != null && result.size < 20) {
            val page = getJson(next, token) ?: break
            val items = page.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val name = item.optString("name").ifBlank { "Playlist de Spotify" }
                val href = item.optJSONObject("tracks")?.optString("href").orEmpty()
                val tracks = if (href.isNotBlank()) tracks(href, token) else emptyList()
                if (tracks.isNotEmpty()) result.add(SpotifyPlaylist(name, tracks))
            }
            next = page.opt("next") as? String
        }
        result
    }

    private suspend fun tracks(url: String, token: String): List<SpotifyTrack> {
        val result = mutableListOf<SpotifyTrack>()
        var next: String? = url
        var pages = 0
        while (next != null && pages < 3) {
            val page = getJson(next, token) ?: break
            val items: JSONArray = page.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val track = items.optJSONObject(i)?.optJSONObject("track") ?: continue
                val id = track.optString("id")
                if (id.isBlank()) continue
                val title = track.optString("name").ifBlank { "Sin título" }
                val artist = track.optJSONArray("artists")
                    ?.optJSONObject(0)?.optString("name").orEmpty().ifBlank { "Artista desconocido" }
                val duration = track.optLong("duration_ms", 0L) / 1000L
                val artwork = track.optJSONObject("album")
                    ?.optJSONArray("images")
                    ?.optJSONObject(0)?.optString("url").orEmpty()
                result.add(SpotifyTrack(id, title, artist, duration, artwork))
            }
            next = page.opt("next") as? String
            pages++
        }
        return result
    }
}
