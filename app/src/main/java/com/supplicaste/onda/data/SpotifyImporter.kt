package com.supplicaste.onda.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

/**
 * Lee el perfil y las playlists de Spotify.
 *
 * El token del reproductor web solo funciona si se envían los mismos headers
 * que la web de Spotify (`app-platform`, `Origin`, `Referer` y un User-Agent
 * de navegador). Si la REST API rechaza el perfil se usa la API GraphQL
 * interna del reproductor, igual que hace ArchiveTune.
 */
class SpotifyImporter {
    private val client = OkHttpClient()

    private fun request(url: String, token: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", USER_AGENT)
            .header("app-platform", "WebPlayer")
            .header("Origin", "https://open.spotify.com")
            .header("Referer", "https://open.spotify.com/")
            .header("Accept", "application/json")
            .header("Accept-Language", "en")

    private fun getJson(url: String, token: String): JSONObject? = runCatching {
        client.newCall(request(url, token).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
        }
    }.getOrNull()

    /** Perfil por REST; si falla, se consulta GraphQL (`profileAttributes`). */
    suspend fun profile(token: String): SpotifyProfile? = withContext(Dispatchers.IO) {
        val json = getJson("https://api.spotify.com/v1/me", token)
        if (json != null) {
            val id = json.optString("id")
            val name = json.optString("display_name")
            if (id.isNotBlank() || name.isNotBlank()) {
                return@withContext SpotifyProfile(
                    id = id,
                    email = json.optString("email"),
                    displayName = name.ifBlank { id }
                )
            }
        }
        profileFromGraphql(token)
    }

    /** Perfil con la query persistida que usa el reproductor web de Spotify. */
    private fun profileFromGraphql(token: String): SpotifyProfile? {
        val body = JSONObject()
            .put("variables", JSONObject())
            .put("operationName", "profileAttributes")
            .put(
                "extensions",
                JSONObject().put(
                    "persistedQuery",
                    JSONObject().put("version", 1).put("sha256Hash", PROFILE_HASH)
                )
            )
            .toString()
            .toRequestBody(JSON_TYPE)

        return runCatching {
            client.newCall(request(GQL_URL, token).post(body).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val root = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                    ?: return null
                val profile = root.optJSONObject("data")?.optJSONObject("me")?.optJSONObject("profile")
                    ?: return null
                val id = profile.optString("uri").substringAfterLast(":")
                val name = profile.optString("name").ifBlank { id }
                if (id.isBlank() && name.isBlank()) return null
                // GraphQL del reproductor web no expone el correo; se usa el nombre.
                SpotifyProfile(id = id, email = "", displayName = name)
            }
        }.getOrNull()
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

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        const val GQL_URL = "https://api-partner.spotify.com/pathfinder/v2/query"
        const val PROFILE_HASH =
            "53bcb064f6cd18c23f752bc324a791194d20df612d8e1239c735144ab0399ced"
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
