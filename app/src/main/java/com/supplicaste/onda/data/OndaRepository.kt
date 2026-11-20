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

private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Onda/1.0"

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

    val albums = dao.albums()

    fun songsInAlbum(albumId: Long) = dao.songsInAlbum(albumId)

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        ensureNewPipe()
        try {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .take(25)
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun resolveStream(song: Song): String? = withContext(Dispatchers.IO) {
        ensureNewPipe()
        try {
            val extractor = ServiceList.YouTube.getStreamExtractor(song.sourceUrl)
            extractor.fetchPage()
            extractor.audioStreams.maxByOrNull { it.averageBitrate }?.url
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createAlbum(name: String): Long = withContext(Dispatchers.IO) {
        dao.insertAlbum(AlbumEntity(name = name.trim().ifBlank { "Álbum sin nombre" }))
    }

    suspend fun addToAlbum(albumId: Long, song: Song, position: Int) = withContext(Dispatchers.IO) {
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
        dao.insertAlbumSong(AlbumSongEntity(albumId, song.id, position))
    }

    suspend fun removeFromAlbum(albumId: Long, songId: String) = withContext(Dispatchers.IO) {
        dao.removeFromAlbum(albumId, songId)
    }

    suspend fun deleteAlbum(albumId: Long) = withContext(Dispatchers.IO) {
        dao.clearAlbum(albumId)
        dao.deleteAlbum(albumId)
    }

    suspend fun recordPlay(song: Song) = withContext(Dispatchers.IO) {
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
        dao.insertHistory(HistoryEntity(songId = song.id))
    }

    suspend fun suggest(candidates: List<Song>): Song? = withContext(Dispatchers.IO) {
        val saved = dao.allSavedSongs().map { it.toSong() }
        val pool = (candidates + saved).distinctBy { it.id }
        if (pool.isEmpty()) return@withContext null

        val history = dao.recentHistory()
        val idById = history.groupingBy { it.songId }.eachCount()
        val artistById = saved.associateBy { it.id }
        val artistPlays = history
            .mapNotNull { artistById[it.songId]?.artist }
            .groupingBy { it }
            .eachCount()

        pool.maxByOrNull { song ->
            (idById[song.id] ?: 0) * 2 + (artistPlays[song.artist] ?: 0)
        }
    }

    suspend fun exportAlbum(name: String): String = withContext(Dispatchers.IO) {
        val songs = dao.allSavedSongs().take(20)
        buildString {
            append("ONDA1|").append(name.replace('|', ' ')).append('|')
            songs.joinTo(this, ";") { song ->
                listOf(song.title, song.artist, song.sourceUrl)
                    .joinToString("¬") { it.replace('¬', ' ').replace(';', ' ') }
            }
        }
    }

    fun importAlbum(payload: String): Pair<String, List<Song>>? {
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
