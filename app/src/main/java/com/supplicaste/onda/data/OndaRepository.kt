package com.supplicaste.onda.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException

private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Onda/1.1"

private class OkHttpDownloader : Downloader() {
    private val client = OkHttpClient.Builder().followRedirects(true).build()

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())
        request.headers().forEach { (name, values) ->
            values.forEach { builder.addHeader(name, it) }
        }
        builder.header("User-Agent", USER_AGENT)

        val method = request.httpMethod()
        val body = if (method == "GET" || method == "HEAD") {
            null
        } else {
            (request.dataToSend() ?: ByteArray(0))
                .toRequestBody("application/octet-stream".toMediaTypeOrNull())
        }
        builder.method(method, body)

        return client.newCall(builder.build()).execute().use { response ->
            val headers = mutableMapOf<String, MutableList<String>>()
            for (i in 0 until response.headers.size) {
                val name = response.headers.name(i)
                headers.getOrPut(name) { mutableListOf() }.add(response.headers.value(i))
            }
            Response(
                response.code,
                response.message,
                headers.mapValues { it.value.toList() },
                response.body?.string(),
                response.request.url.toString()
            )
        }
    }
}

class OndaRepository(private val dao: OndaDao) {

    companion object {
        @Volatile
        private var initialized = false

        private fun ensureNewPipe() {
            if (initialized) return
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(OkHttpDownloader())
                    initialized = true
                }
            }
        }
    }

    private val streamCache = mutableMapOf<String, String>()

    val albums = dao.albums()
    val playlists = dao.playlists()
    val likedSongs = dao.likedSongs()
    val likedIds = dao.likedIds()
    val recentSongs = dao.recentSongs(12)
    val topArtists = dao.topArtists(6)
    val stats = dao.stats()
    val recentSearches = dao.recentSearches(8)

    fun songsInAlbum(albumId: Long) = dao.songsInAlbum(albumId)

    fun songsOfPlaylist(playlistId: Long) =
        if (playlistId == LIKED_PLAYLIST_ID) dao.likedSongs() else dao.songsInPlaylist(playlistId)

    suspend fun songsOfPlaylistOnce(playlistId: Long): List<SongEntity> = withContext(Dispatchers.IO) {
        if (playlistId == LIKED_PLAYLIST_ID) dao.likedSongsOnce() else dao.songsInPlaylistOnce(playlistId)
    }

    suspend fun allSongs(): List<Song> = withContext(Dispatchers.IO) {
        dao.allSongs().map { it.toSong() }
    }

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()
        ensureNewPipe()
        try {
            val extractor = ServiceList.YouTube.getSearchExtractor(clean)
            extractor.fetchPage()
            val found = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .take(30)
                .map { item ->
                    Song(
                        id = item.url.hashCode().toString(),
                        title = item.name,
                        artist = item.uploaderName,
                        durationSec = item.duration,
                        artwork = item.thumbnails.firstOrNull()?.url.orEmpty(),
                        sourceUrl = item.url
                    )
                }
            dao.deleteSearch(clean)
            dao.insertSearch(SearchHistoryEntity(query = clean))
            found
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun rememberSearch(query: String) = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext
        dao.deleteSearch(clean)
        dao.insertSearch(SearchHistoryEntity(query = clean))
    }

    suspend fun clearSearches() = withContext(Dispatchers.IO) { dao.clearSearches() }

    suspend fun deleteSearch(query: String) = withContext(Dispatchers.IO) { dao.deleteSearch(query) }

    suspend fun resolveStream(song: Song): String? = withContext(Dispatchers.IO) {
        streamCache[song.sourceUrl]?.let { return@withContext it }
        ensureNewPipe()
        try {
            val extractor = ServiceList.YouTube.getStreamExtractor(song.sourceUrl)
            extractor.fetchPage()
            val url = extractor.audioStreams.maxByOrNull { it.averageBitrate }?.url
            if (url != null) streamCache[song.sourceUrl] = url
            url
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun upsert(song: Song) {
        dao.upsertSong(
            SongEntity(
                id = song.id,
                title = song.title,
                artist = song.artist,
                durationSec = song.durationSec,
                artwork = song.artwork,
                sourceUrl = song.sourceUrl
            )
        )
    }

    suspend fun createAlbum(name: String): Long = withContext(Dispatchers.IO) {
        dao.insertAlbum(AlbumEntity(name = name.trim().ifBlank { "Álbum sin nombre" }))
    }

    suspend fun addToAlbum(albumId: Long, song: Song) = withContext(Dispatchers.IO) {
        upsert(song)
        dao.insertAlbumSong(AlbumSongEntity(albumId, song.id, dao.songsInAlbumOnce(albumId).size))
    }

    suspend fun removeFromAlbum(albumId: Long, songId: String) = withContext(Dispatchers.IO) {
        dao.removeFromAlbum(albumId, songId)
    }

    suspend fun deleteAlbum(albumId: Long) = withContext(Dispatchers.IO) {
        dao.clearAlbum(albumId)
        dao.deleteAlbum(albumId)
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        dao.insertPlaylist(PlaylistEntity(name = name.trim().ifBlank { "Nueva playlist" }))
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) = withContext(Dispatchers.IO) {
        dao.renamePlaylist(playlistId, name.trim().ifBlank { "Playlist" })
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.clearPlaylist(playlistId)
        dao.deletePlaylist(playlistId)
    }

    suspend fun addToPlaylist(playlistId: Long, song: Song) = withContext(Dispatchers.IO) {
        if (playlistId == LIKED_PLAYLIST_ID) {
            setLiked(song, true)
        } else {
            upsert(song)
            dao.insertPlaylistSong(
                PlaylistSongEntity(playlistId, song.id, dao.songsInPlaylistOnce(playlistId).size)
            )
        }
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: String) = withContext(Dispatchers.IO) {
        if (playlistId == LIKED_PLAYLIST_ID) dao.deleteLiked(songId) else dao.removeFromPlaylist(playlistId, songId)
    }

    suspend fun setLiked(song: Song, liked: Boolean): Boolean = withContext(Dispatchers.IO) {
        upsert(song)
        if (liked) dao.insertLiked(LikedSongEntity(song.id)) else dao.deleteLiked(song.id)
        liked
    }

    suspend fun recordPlay(song: Song) = withContext(Dispatchers.IO) {
        upsert(song)
        dao.insertHistory(HistoryEntity(songId = song.id))
    }

    suspend fun suggest(candidates: List<Song>, current: Song? = null): Song? = withContext(Dispatchers.IO) {
        val all = dao.allSongs().map { it.toSong() }
        val pool = (candidates + all).distinctBy { it.id }
            .filter { it.id != current?.id }
        if (pool.isEmpty()) return@withContext null

        val liked = dao.likedSongsOnce().map { it.id }.toSet()
        val collections = dao.collectionSongIds().toSet()
        val history = dao.recentHistory()
        val playsById = history.groupingBy { it.songId }.eachCount()
        val artistById = all.associateBy { it.id }
        val artistPlays = history.mapNotNull { artistById[it.songId]?.artist }
            .groupingBy { it }.eachCount()

        // Contexto de la canción actual: artista y título para buscar canciones afines.
        val currentArtist = current?.artist.orBlankLower()
        val relatedTitles = if (currentArtist.isNotBlank()) {
            try {
                ensureNewPipe()
                ServiceList.YouTube.getSearchExtractor(current!!.artist)
                    .apply { fetchPage() }
                    .initialPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .take(12)
                    .map { it.name.orBlankLower() }
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()

        pool.maxByOrNull { song ->
            val title = song.title.orBlankLower()
            (if (current != null && song.artist.orBlankLower() == currentArtist) 5 else 0) +
                (if (song.id in liked) 3 else 0) +
                (if (song.id in collections) 2 else 0) +
                (playsById[song.id] ?: 0) * 2 +
                (artistPlays[song.artist] ?: 0) +
                (if (relatedTitles.any { it.contains(title) || title.contains(it) }) 2 else 0)
        }
    }

    private fun String?.orBlankLower(): String = this?.trim()?.lowercase().orEmpty()

    suspend fun exportList(name: String, songs: List<Song>): String = withContext(Dispatchers.IO) {
        buildString {
            append("ONDA1|").append(name.replace('|', ' ')).append('|')
            songs.take(30).joinTo(this, ";") { song ->
                listOf(song.title, song.artist, song.sourceUrl)
                    .joinToString("¬") { it.replace('¬', ' ').replace(';', ' ') }
            }
        }
    }

    fun importList(payload: String): Pair<String, List<Song>>? {
        val parts = payload.split("|")
        if (parts.size < 3 || parts[0] != "ONDA1") return null
        val name = parts[1]
        val songs = parts[2].split(";").mapNotNull { entry ->
            val fields = entry.split("¬")
            if (fields.size != 3) return@mapNotNull null
            Song(
                id = fields[2].hashCode().toString(),
                title = fields[0],
                artist = fields[1],
                durationSec = 0,
                artwork = "",
                sourceUrl = fields[2]
            )
        }
        return name to songs
    }
}
