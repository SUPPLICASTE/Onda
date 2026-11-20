package com.supplicaste.onda.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
 * Lee la biblioteca usando las mismas consultas que el reproductor web.
 *
 * Los endpoints REST de api.spotify.com responden 429 al token del reproductor
 * web, así que playlists, favoritos, álbumes y artistas se leen por GraphQL
 * (`api-partner.spotify.com`). Los hashes se actualizan desde un registro
 * público; si no hay red se usan los valores incluidos.
 */
class SpotifyImporter {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()

    private fun baseRequest(url: String, token: String? = null): Request.Builder =
        Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("app-platform", "WebPlayer")
            .header("Origin", "https://open.spotify.com")
            .header("Referer", "https://open.spotify.com/")
            .header("Accept", "application/json")
            .header("Accept-Language", "en")
            .apply { if (token != null) header("Authorization", "Bearer $token") }

    private fun getJson(url: String, token: String? = null): JSONObject? = runCatching {
        client.newCall(baseRequest(url, token).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
        }
    }.getOrNull()

    /** Perfil por GraphQL; si falla, se intenta la REST clásica. */
    suspend fun profile(token: String): SpotifyProfile? = withContext(Dispatchers.IO) {
        val response = gqlPost(token, "profileAttributes", JSONObject())
        val data = response?.at("data")?.at("me")?.at("profile")
        if (data != null) {
            val uri = data.optString("uri")
            val id = uri.substringAfterLast(":").ifBlank { data.optString("id") }
            val name = data.optString("name")
                .ifBlank { data.optString("username") }
                .ifBlank { id }
            if (id.isNotBlank() || name.isNotBlank()) {
                return@withContext SpotifyProfile(id = id, email = "", displayName = name)
            }
        }
        val json = getJson("https://api.spotify.com/v1/me", token)
        val id = json?.optString("id").orEmpty()
        val name = json?.optString("display_name").orEmpty()
        if (id.isBlank() && name.isBlank()) return@withContext null
        SpotifyProfile(id, json?.optString("email").orEmpty(), name.ifBlank { id })
    }

    /** Todas las playlists del usuario, con todas sus canciones. */
    suspend fun playlists(token: String): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        libraryItems(token, "Playlists", MAX_PLAYLISTS).mapNotNull { wrapper ->
            val data = wrapper.at("data") ?: return@mapNotNull null
            val uri = wrapper.optString("_uri").ifBlank { data.optString("uri") }
            val id = uri.substringAfterLast(":")
            if (id.isBlank()) return@mapNotNull null
            val name = data.optString("name").ifBlank { "Playlist de Spotify" }
            SpotifyPlaylist(name, playlistTracks(token, id))
        }
    }

    /** Canciones de "Me gusta" (`fetchLibraryTracks`). */
    suspend fun likedTracks(token: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SpotifyTrack>()
        var offset = 0
        while (offset < MAX_LIKED) {
            val vars = JSONObject().put("offset", offset).put("limit", 100)
            val tracks = gqlPost(token, "fetchLibraryTracks", vars)
                ?.at("data")?.at("me")?.at("library")?.at("tracks")
                ?.optJSONArray("items") ?: break
            var found = 0
            for (i in 0 until tracks.length()) {
                val wrapper = tracks.optJSONObject(i)?.at("track") ?: continue
                val data = wrapper.at("data") ?: continue
                val uri = wrapper.optString("_uri").ifBlank { wrapper.optString("uri") }
                parseGqlTrack(data, uri)?.let { result.add(it); found++ }
            }
            if (found == 0 || tracks.length() < 100) break
            offset += found
        }
        result
    }

    /** Álbumes guardados en la biblioteca, con sus canciones. */
    suspend fun savedAlbums(token: String): List<SpotifyAlbum> = withContext(Dispatchers.IO) {
        libraryItems(token, "Albums", MAX_ALBUMS).mapNotNull { wrapper ->
            val data = wrapper.at("data") ?: return@mapNotNull null
            val uri = wrapper.optString("_uri").ifBlank { data.optString("uri") }
            val id = uri.substringAfterLast(":")
            if (id.isBlank()) return@mapNotNull null
            val name = data.optString("name").ifBlank { "Álbum de Spotify" }
            val artist = data.optJSONArray("artists")
                ?.optJSONObject(0)?.at("profile")?.optString("name").orEmpty()
                .ifBlank { "Artista desconocido" }
            val artwork = largestSource(data.at("coverArt")?.optJSONArray("sources"))
            SpotifyAlbum(name, artist, artwork, albumTracks(token, id, artwork))
        }
    }

    /** Artistas seguidos y una canción destacada de cada uno. */
    suspend fun followedArtists(token: String): Pair<List<SpotifyArtist>, List<SpotifyTrack>> =
        withContext(Dispatchers.IO) {
            val artists = libraryItems(token, "Artists", MAX_ARTISTS).mapNotNull { wrapper ->
                val data = wrapper.at("data") ?: return@mapNotNull null
                val uri = wrapper.optString("_uri").ifBlank { data.optString("uri") }
                val id = uri.substringAfterLast(":")
                val name = data.at("profile")?.optString("name").orEmpty()
                if (id.isBlank() || name.isBlank()) null else SpotifyArtist(id, name)
            }

            val tracks = mutableListOf<SpotifyTrack>()
            artists.take(MAX_ARTISTS).forEach { artist ->
                val vars = JSONObject()
                    .put("searchTerm", artist.name)
                    .put("offset", 0)
                    .put("limit", 1)
                    .put("numberOfTopResults", 1)
                    .put("includeAudiobooks", false)
                    .put("includePreReleases", false)
                    .put("includeAuthors", false)
                val items = gqlPost(token, "searchTracks", vars)
                    ?.at("data")?.at("searchV2")?.at("tracksV2")?.optJSONArray("items")
                    ?: return@forEach
                for (i in 0 until minOf(items.length(), 1)) {
                    val wrapper = items.optJSONObject(i)?.at("item") ?: continue
                    val data = wrapper.at("data") ?: continue
                    val uri = data.optString("uri").ifBlank { data.optString("_uri") }
                    parseGqlTrack(data, uri)?.let(tracks::add)
                }
            }
            artists to tracks
        }

    /** Elementos de la biblioteca (`libraryV3`) de un tipo dado. */
    private fun libraryItems(token: String, filter: String, max: Int): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        var offset = 0
        while (offset < max) {
            val vars = JSONObject()
                .put("filters", JSONArray().put(filter))
                .put("order", JSONObject.NULL)
                .put("textFilter", "")
                .put(
                    "features",
                    JSONArray()
                        .put("LIKED_SONGS")
                        .put("YOUR_EPISODES_V2")
                        .put("PRERELEASES")
                        .put("EVENTS")
                )
                .put("limit", 50)
                .put("offset", offset)
                .put("flatten", true)
                .put("expandedFolders", JSONArray())
                .put("folderUri", JSONObject.NULL)
                .put("includeFoldersWhenFlattening", false)
            val library = gqlPost(token, "libraryV3", vars)
                ?.at("data")?.at("me")?.at("libraryV3") ?: break
            val items = library.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val wrapper = items.optJSONObject(i)?.at("item") ?: continue
                result.add(wrapper)
            }
            if (items.length() < 50) break
            offset += items.length()
        }
        return result.take(max)
    }

    /** Canciones de una playlist (`fetchPlaylist`). */
    private fun playlistTracks(token: String, playlistId: String): List<SpotifyTrack> {
        val result = mutableListOf<SpotifyTrack>()
        var offset = 0
        while (result.size < MAX_TRACKS_PER_LIST) {
            val vars = JSONObject()
                .put("uri", "spotify:playlist:$playlistId")
                .put("offset", offset)
                .put("limit", 100)
                .put("enableWatchFeedEntrypoint", false)
            val content = gqlPost(token, "fetchPlaylist", vars)
                ?.at("data")?.at("playlistV2")?.at("content") ?: break
            val items = content.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val wrapper = items.optJSONObject(i)?.at("itemV2") ?: continue
                val data = wrapper.at("data") ?: continue
                val uri = data.optString("uri").ifBlank { data.optString("_uri") }
                parseGqlTrack(data, uri)?.let(result::add)
            }
            val total = content.optInt("totalCount", -1)
            if (items.length() < 100 || (total >= 0 && offset + items.length() >= total)) break
            offset += items.length()
        }
        return result
    }

    /** Canciones de un álbum (`getAlbum`). */
    private fun albumTracks(token: String, albumId: String, cover: String): List<SpotifyTrack> {
        val result = mutableListOf<SpotifyTrack>()
        var offset = 0
        while (result.size < MAX_TRACKS_PER_LIST) {
            val vars = JSONObject()
                .put("uri", "spotify:album:$albumId")
                .put("locale", "")
                .put("offset", offset)
                .put("limit", 100)
            val album = gqlPost(token, "getAlbum", vars)?.at("data")?.at("albumUnion") ?: break
            val tracksV2 = album.at("tracksV2") ?: break
            val items = tracksV2.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                val wrapper = entry.at("track") ?: entry.at("itemV2") ?: continue
                val data = wrapper.at("data") ?: wrapper
                val uri = data.optString("uri").ifBlank { data.optString("_uri") }
                    .ifBlank { wrapper.optString("_uri") }
                parseGqlTrack(data, uri, cover)?.let(result::add)
            }
            val total = tracksV2.optInt("totalCount", -1)
            if (items.length() < 100 || (total >= 0 && offset + items.length() >= total)) break
            offset += items.length()
        }
        return result
    }

    private fun parseGqlTrack(
        data: JSONObject,
        uri: String,
        albumArtwork: String = ""
    ): SpotifyTrack? {
        val id = uri.substringAfterLast(":")
        if (id.isBlank()) return null
        val title = data.optString("name").ifBlank { "Sin título" }
        val artist = data.optJSONArray("artists")
            ?.optJSONObject(0)?.at("profile")?.optString("name").orEmpty()
            .ifBlank { "Artista desconocido" }
        val durationMs = data.at("duration")?.optLong("totalMilliseconds", 0L)
            ?.takeIf { it > 0L }
            ?: data.at("trackDuration")?.optLong("totalMilliseconds", 0L)
                ?.takeIf { it > 0L }
            ?: data.optLong("durationMs", 0L)
        val artwork = data.at("albumOfTrack")?.at("coverArt")?.optJSONArray("sources")
            .let(::largestSource).ifBlank { albumArtwork }
        return SpotifyTrack(id, title, artist, durationMs / 1000L, artwork)
    }

    private fun largestSource(sources: JSONArray?): String {
        if (sources == null) return ""
        var best = ""
        var bestWidth = -1
        for (i in 0 until sources.length()) {
            val source = sources.optJSONObject(i) ?: continue
            val url = source.optString("url")
            val width = source.optInt("width", 0)
            if (url.isNotBlank() && width > bestWidth) {
                best = url
                bestWidth = width
            }
        }
        return best
    }

    private fun JSONObject.at(key: String): JSONObject? = this.opt(key) as? JSONObject

    /** Consulta GraphQL con una query persistida del reproductor web. */
    private fun gqlPost(
        token: String,
        operationName: String,
        variables: JSONObject,
        retry: Boolean = true
    ): JSONObject? {
        val hash = hashFor(operationName)
        if (hash.isBlank()) return null
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
        val response = runCatching {
            val request = baseRequest(GQL_URL, token).post(body).build()
            client.newCall(request).execute().use { result ->
                val text = result.body?.string().orEmpty()
                if (!result.isSuccessful) return@use null
                runCatching { JSONObject(text) }.getOrNull()
            }
        }.getOrNull() ?: return null

        val errors = response.optJSONArray("errors")
        if (errors != null && errors.length() > 0 && retry) {
            val message = (0 until errors.length()).joinToString { errors.optJSONObject(it)?.optString("message").orEmpty() }
            if (message.contains("PersistedQueryNotFound", true)) {
                hashesLoaded = false
                return gqlPost(token, operationName, variables, retry = false)
            }
        }
        return response.takeIf { it.has("data") }
    }

    /** Los hashes rotan con cada deploy de Spotify: se leen del registro público. */
    private fun hashFor(operation: String): String {
        if (!hashesLoaded) {
            hashesLoaded = true
            val json = getJson(HASH_REGISTRY)
            val operations = json?.optJSONObject("operations")
            if (operations != null) {
                val fresh = mutableMapOf<String, String>()
                val names = operations.keys()
                while (names.hasNext()) {
                    val name = names.next()
                    val hash = operations.optJSONObject(name)?.optString("hash").orEmpty()
                    if (hash.length == 64) fresh[name] = hash
                }
                if (fresh.isNotEmpty()) cachedHashes = fresh + DEFAULT_HASHES
            }
        }
        return cachedHashes[operation].orEmpty()
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        const val GQL_URL = "https://api-partner.spotify.com/pathfinder/v2/query"
        const val HASH_REGISTRY =
            "https://raw.githubusercontent.com/Jigen-Ohtsusuki/spotify-gql-registry/main/hashes.json"
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        const val MAX_PLAYLISTS = 100
        const val MAX_TRACKS_PER_LIST = 500
        const val MAX_LIKED = 1000
        const val MAX_ALBUMS = 100
        const val MAX_ARTISTS = 30

        val DEFAULT_HASHES = mapOf(
            "profileAttributes" to "08ffb4730af3746e04a8301396f20875dbbce10c75243803091a9274eacc8ac0",
            "libraryV3" to "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455",
            "fetchPlaylist" to "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf",
            "fetchLibraryTracks" to "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240",
            "getAlbum" to "0b6ce3511e1b02e4a40fa95440f992acbe85080eb308af9194322d708fc1914f",
            "searchTracks" to "b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751"
        )

        @Volatile
        private var hashesLoaded = false

        @Volatile
        private var cachedHashes: Map<String, String> = DEFAULT_HASHES
    }
}
