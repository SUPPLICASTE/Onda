package com.supplicaste.onda.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Importa las playlists propias de YouTube con el token OAuth del usuario. */
class YouTubeImporter {
    private val client = OkHttpClient()

    private suspend fun getJson(url: String, token: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun playlists(token: String): List<ProviderPlaylist> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ProviderPlaylist>()
        var pageToken: String? = ""
        while (pageToken != null && result.size < 30) {
            val url = "https://youtube.googleapis.com/youtube/v3/playlists" +
                "?part=snippet&mine=true&maxResults=50" +
                (if (pageToken.isNotBlank()) "&pageToken=$pageToken" else "")
            val json = getJson(url, token) ?: break
            val items = json.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val snippet = item.optJSONObject("snippet")
                val name = snippet?.optString("title").orEmpty().ifBlank { "Playlist de YouTube" }
                val tracks = playlistItems(id, token)
                if (tracks.isNotEmpty()) result.add(ProviderPlaylist(name, tracks))
            }
            val next = json.optString("nextPageToken")
            pageToken = next.ifBlank { null }
        }
        result
    }

    private suspend fun playlistItems(playlistId: String, token: String): List<ProviderTrack> {
        val result = mutableListOf<ProviderTrack>()
        var pageToken: String? = ""
        while (pageToken != null && result.size < 200) {
            val url = "https://youtube.googleapis.com/youtube/v3/playlistItems" +
                "?part=snippet&playlistId=$playlistId&maxResults=50" +
                (if (pageToken.isNotBlank()) "&pageToken=$pageToken" else "")
            val json = getJson(url, token) ?: break
            val items = json.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val snippet = items.optJSONObject(i)?.optJSONObject("snippet") ?: continue
                val videoId = snippet.optJSONObject("resourceId")?.optString("videoId").orEmpty()
                if (videoId.isBlank()) continue
                val title = snippet.optString("title").ifBlank { "Sin título" }
                val artist = snippet.optString("videoOwnerChannelTitle")
                    .ifBlank { snippet.optString("channelTitle") }
                    .ifBlank { "YouTube" }
                val artwork = snippet.optJSONObject("thumbnails")
                    ?.optJSONObject("high")?.optString("url")
                    .orEmpty()
                    .ifBlank {
                        snippet.optJSONObject("thumbnails")
                            ?.optJSONObject("default")?.optString("url").orEmpty()
                    }
                result.add(
                    ProviderTrack(
                        id = videoId,
                        title = title,
                        artist = artist,
                        durationSec = 0,
                        artwork = artwork,
                        sourceUrl = "https://www.youtube.com/watch?v=$videoId"
                    )
                )
            }
            val next = json.optString("nextPageToken")
            pageToken = next.ifBlank { null }
        }
        return result
    }
}
