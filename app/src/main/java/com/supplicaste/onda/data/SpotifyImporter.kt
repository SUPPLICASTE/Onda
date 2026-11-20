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

data class SpotifyAlbum(
    val name: String,
    val artist: String,
    val artwork: String,
    val tracks: List<SpotifyTrack>
)

data class SpotifyArtist(
    val id: String,
    val name: String
)

/** Toda la biblioteca de Spotify que se traslada a ONDA al iniciar sesión. */
data class SpotifyLibrary(
    val profile: SpotifyProfile,
    val playlists: List<SpotifyPlaylist>,
    val liked: List<SpotifyTrack>,
    val albums: List<SpotifyAlbum>,
    val artists: List<SpotifyArtist>,
    val artistTracks: List<SpotifyTrack>
)

/**
 * Lee el perfil y la biblioteca de Spotify.
 *
 * El token del reproductor web solo funciona si se envían los mismos headers
 * que la web de Spotify. Para las canciones guardadas se usa la API GraphQL
 * interna como respaldo, igual que hace ArchiveTune.
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
        val response = gqlPost(token, "profileAttributes", JSONObject())
            ?: return@withContext null
        val data = response.optJSONObject("data")?.optJSONObject("me")?.optJSONObject("profile")
            ?: return@withContext null
        val id = data.optString("uri").substringAfterLast(":")
        val name = data.optString("name").ifBlank { id }
        if (id.isBlank() && name.isBlank()) return@withContext null
        SpotifyProfile(id = id, email = "", displayName = name)
    }

    /** Playlists del usuario (todas las páginas, sin el límite anterior de 20). */
    suspend fun playlists(token: String): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SpotifyPlaylist>()
        var next: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
        while (next != null && result.size < MAX_PLAYLISTS) {
            val page = getJson(next, token) ?: break
            val items = page.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val name = item.optString("name").ifBlank { "Playlist de Spotify" }
                val href = item.optJSONObject("tracks")?.optString("href").orEmpty()
                val tracks = if (href.isNotBlank()) pagedTracks(href, token, wrapped = true) else emptyList()
                result.add(SpotifyPlaylist(name, tracks))
            }
            next = page.opt("next") as? String
        }
        result
    }

    /** Canciones de "Me gusta": REST y, si falla, GraphQL (`fetchLibraryTracks`). */
    suspend fun likedTracks(token: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SpotifyTrack>()
        var next: String? = "https://api.spotify.com/v1/me/tracks?limit=50"
        while (next != null && result.size < MAX_LIKED) {
            val page = getJson(next, token) ?: break
            val items = page.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                parseTrack(items.optJSONObject(i)?.optJSONObject("track"))?.let(result::add)
            }
            next = page.opt("next") as? String
        }
        if (result.isEmpty()) likedTracksFromGraphql(token) else result
    }

    private fun likedTracksFromGraphql(token: String): List<SpotifyTrack> {
        val result = mutableListOf<SpotifyTrack>()
        var offset = 0
        while (offset < MAX_LIKED) {
            val vars = JSONObject().put("offset", offset).put("limit", 50)
            val response = gqlPost(token, "fetchLibraryTracks", vars) ?: break
            val tracks = response.optJSONObject("data")
                ?.optJSONObject("me")?.optJSONObject("library")?.optJSONObject("tracks")
                ?.optJSONArray("items") ?: break
            var found = 0
            for (i in 0 until tracks.length()) {
                val wrapper = tracks.optJSONObject(i)?.optJSONObject("track") ?: continue
                val data = wrapper.optJSONObject("data") ?: continue
                val uri = wrapper.optString("_uri").ifBlank { wrapper.optString("uri") }
                parseGqlTrack(data, uri)?.let { result.add(it); found++ }
            }
            if (found == 0 || tracks.length() < 50) break
            offset += found
        }
        return result
    }

    /** Álbumes guardados en la biblioteca, con sus canciones. */
    suspend fun savedAlbums(token: String): List<SpotifyAlbum> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SpotifyAlbum>()
        var next: String? = "https://api.spotify.com/v1/me/albums?limit=50"
        while (next != null && result.size < MAX_ALBUMS) {
            val page = getJson(next, token) ?: break
            val items = page.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val album = items.optJSONObject(i)?.optJSONObject("album") ?: continue
                val name = album.optString("name").ifBlank { "Álbum de Spotify" }
                val artist = album.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty()
                val artwork = album.optJSONArray("images")?.optJSONObject(0)?.optString("url").orEmpty()
                val href = album.optJSONObject("tracks")?.optString("href").orEmpty()
                val tracks = if (href.isNotBlank()) pagedTracks(href, token, wrapped = false) else emptyList()
                result.add(SpotifyAlbum(name, artist, artwork, tracks))
            }
            next = page.opt("next") as? String
        }
        result
    }

    /** Artistas seguidos y una canción destacada de cada uno. */
    suspend fun followedArtists(token: String): Pair<List<SpotifyArtist>, List<SpotifyTrack>> =
        withContext(Dispatchers.IO) {
            val artists = mutableListOf<SpotifyArtist>()
            var next: String? = "https://api.spotify.com/v1/me/following?type=artist&limit=50"
            while (next != null && artists.size < MAX_ARTISTS) {
                val page = getJson(next, token) ?: break
                val items = page.optJSONObject("artists")?.optJSONArray("items") ?: break
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (id.isNotBlank()) artists.add(SpotifyArtist(id, item.optString("name")))
                }
                next = page.optJSONObject("artists")?.opt("next") as? String
            }

            val tracks = mutableListOf<SpotifyTrack>()
            artists.take(MAX_ARTISTS).forEach { artist ->
                val top = getJson(
                    "https://api.spotify.com/v1/artists/${artist.id}/top-tracks?limit=3",
                    token
                )?.optJSONArray("tracks") ?: return@forEach
                for (i in 0 until minOf(top.length(), 3)) {
                    parseTrack(top.optJSONObject(i))?.let(tracks::add)
                }
            }
            artists to tracks
        }

    /** Carga todas las páginas de un endpoint de canciones. */
    private fun pagedTracks(url: String, token: String, wrapped: Boolean): List<SpotifyTrack> {
        val result = mutableListOf<SpotifyTrack>()
        var next: String? = url
        var pages = 0
        while (next != null && pages < MAX_TRACK_PAGES && result.size < MAX_TRACKS_PER_LIST) {
            val page = getJson(next, token) ?: break
            val items: JSONArray = page.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                val track = if (wrapped) entry.optJSONObject("track") else entry
                parseTrack(track)?.let(result::add)
            }
            next = page.opt("next") as? String
            pages++
        }
        return result
    }

    private fun parseTrack(track: JSONObject?): SpotifyTrack? {
        if (track == null) return null
        val id = track.optString("id")
        if (id.isBlank()) return null
        val title = track.optString("name").ifBlank { "Sin título" }
        val artist = track.optJSONArray("artists")
            ?.optJSONObject(0)?.optString("name").orEmpty().ifBlank { "Artista desconocido" }
        val duration = track.optLong("duration_ms", 0L) / 1000L
        val artwork = track.optJSONObject("album")
            ?.optJSONArray("images")
            ?.optJSONObject(0)?.optString("url").orEmpty()
        return SpotifyTrack(id, title, artist, duration, artwork)
    }

    private fun parseGqlTrack(data: JSONObject, uri: String): SpotifyTrack? {
        val id = uri.substringAfterLast(":")
        if (id.isBlank()) return null
        val title = data.optString("name").ifBlank { "Sin título" }
        val artist = data.optJSONObject("artists")
            ?.optJSONArray("items")?.optJSONObject(0)
            ?.optJSONObject("profile")?.optString("name").orEmpty()
            .ifBlank { "Artista desconocido" }
        val durationMs = data.optJSONObject("duration")?.optLong("totalMilliseconds", 0L)
            ?.takeIf { it > 0L }
            ?: data.optLong("durationMs", 0L)
        val artwork = data.optJSONObject("albumOfTrack")
            ?.optJSONObject("coverArt")?.optJSONArray("sources")
            ?.optJSONObject(0)?.optString("url").orEmpty()
        return SpotifyTrack(id, title, artist, durationMs / 1000L, artwork)
    }

    /** Consulta GraphQL con una query persistida del reproductor web. */
    private fun gqlPost(
        token: String,
        operationName: String,
        variables: JSONObject,
    ): JSONObject? {
        val hash = GQL_HASHES[operationName] ?: return null
        val body = JSONObject()
            .put("variables", variables)
            .put("operationName", operationName)
            .put(
                "extensions",
                JSONObject().put(
                    "persistedQuery",
                    JSONObject().put("version", 1).put("sha256Hash", hash)
                )
            )
            .toString()
            .toRequestBody(JSON_TYPE)
        return runCatching {
            client.newCall(request(GQL_URL, token).post(body).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            }
        }.getOrNull()
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        const val GQL_URL = "https://api-partner.spotify.com/pathfinder/v2/query"
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        const val MAX_PLAYLISTS = 100
        const val MAX_TRACK_PAGES = 10
        const val MAX_TRACKS_PER_LIST = 500
        const val MAX_LIKED = 1000
        const val MAX_ALBUMS = 100
        const val MAX_ARTISTS = 30

        val GQL_HASHES = mapOf(
            "profileAttributes" to "53bcb064f6cd18c23f752bc324a791194d20df612d8e1239c735144ab0399ced",
            "fetchLibraryTracks" to "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240"
        )
    }
}
