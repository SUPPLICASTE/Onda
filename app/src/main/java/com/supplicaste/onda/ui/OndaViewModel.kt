package com.supplicaste.onda.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.room.Room
import com.supplicaste.onda.BuildConfig
import com.supplicaste.onda.auth.AuthManager
import com.supplicaste.onda.data.AlbumEntity
import com.supplicaste.onda.data.ArtistCount
import com.supplicaste.onda.data.LIKED_PLAYLIST_ID
import com.supplicaste.onda.data.MIGRATION_1_2
import com.supplicaste.onda.data.OndaDatabase
import com.supplicaste.onda.data.OndaRepository
import com.supplicaste.onda.data.OndaStats
import com.supplicaste.onda.data.Song
import com.supplicaste.onda.data.toSong
import com.supplicaste.onda.player.OndaPlayer
import com.supplicaste.onda.share.QrCodec
import com.supplicaste.onda.updater.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OndaUiState(
    val email: String? = null,
    val authMode: String = "",
    val loading: Boolean = false,
    val message: String = "",
    val results: List<Song> = emptyList(),
    val recent: List<Song> = emptyList(),
    val topArtists: List<ArtistCount> = emptyList(),
    val likedIds: Set<String> = emptySet(),
    val currentSong: Song? = null,
    val playing: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = -1,
    val djEnabled: Boolean = false,
    val darkTheme: Boolean = true,
    val stats: OndaStats = OndaStats(0, 0, 0, 0),
    val qr: Bitmap? = null,
    val updateAvailable: String? = null
)

class OndaViewModel(app: Application) : AndroidViewModel(app) {
    private val database = Room.databaseBuilder(app, OndaDatabase::class.java, "onda.db")
        .addMigrations(MIGRATION_1_2)
        .build()
    private val repository = OndaRepository(database.dao())
    private val auth = AuthManager(app)
    private val prefs = app.getSharedPreferences("onda_prefs", Application.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        OndaUiState(
            email = auth.currentEmail(),
            authMode = auth.modeLabel,
            darkTheme = prefs.getBoolean("darkTheme", true)
        )
    )
    val state: StateFlow<OndaUiState> = _state.asStateFlow()

    val albums = repository.albums
    val playlists = repository.playlists
    val likedSongs = repository.likedSongs
    val recentSearches = repository.recentSearches

    init {
        OndaPlayer.connect(getApplication()) { syncPlayer() }

        viewModelScope.launch {
            repository.likedIds.collect { ids -> _state.value = _state.value.copy(likedIds = ids.toSet()) }
        }
        viewModelScope.launch {
            repository.recentSongs.collect { songs -> _state.value = _state.value.copy(recent = songs.map { it.toSong() }) }
        }
        viewModelScope.launch {
            repository.topArtists.collect { list -> _state.value = _state.value.copy(topArtists = list) }
        }
        viewModelScope.launch {
            repository.stats.collect { value -> _state.value = _state.value.copy(stats = value) }
        }
        viewModelScope.launch {
            OndaBus.imported.collect { code -> importShared(code) }
        }
        viewModelScope.launch {
            while (isActive) {
                syncPlayer()
                delay(500)
            }
        }
    }

    private fun syncPlayer() {
        val controller = OndaPlayer.get() ?: return
        _state.value = _state.value.copy(
            playing = controller.playWhenReady && controller.isPlaying,
            position = controller.currentPosition.coerceAtLeast(0),
            duration = if (controller.duration == C.TIME_UNSET) 0L else controller.duration,
            shuffle = controller.shuffleModeEnabled,
            repeatMode = controller.repeatMode
        )
    }

    fun songsOfAlbum(albumId: Long) = repository.songsInAlbum(albumId)

    fun songsOfPlaylist(playlistId: Long) = repository.songsOfPlaylist(playlistId)

    // ---------- Cuenta ----------

    fun signUp(email: String, password: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, message = "")
        val result = auth.signUp(email, password)
        _state.value = _state.value.copy(
            loading = false,
            email = result.getOrNull(),
            message = result.fold({ "Sesión iniciada" }, { "Error: ${it.message}" })
        )
    }

    fun signIn(email: String, password: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, message = "")
        val result = auth.signIn(email, password)
        _state.value = _state.value.copy(
            loading = false,
            email = result.getOrNull(),
            message = result.fold({ "Sesión iniciada" }, { "Error: ${it.message}" })
        )
    }

    fun signOut() {
        auth.signOut()
        OndaPlayer.get()?.stop()
        _state.value = OndaUiState(
            authMode = auth.modeLabel,
            darkTheme = _state.value.darkTheme,
            message = "Sesión cerrada"
        )
    }

    fun toggleTheme() {
        val value = !_state.value.darkTheme
        prefs.edit { putBoolean("darkTheme", value) }
        _state.value = _state.value.copy(darkTheme = value)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = "")
    }

    fun clearQr() {
        _state.value = _state.value.copy(qr = null)
    }

    fun dismissUpdate() {
        _state.value = _state.value.copy(updateAvailable = null)
    }

    // ---------- Búsqueda ----------

    fun search(query: String) = viewModelScope.launch {
        if (query.isBlank()) return@launch
        _state.value = _state.value.copy(loading = true, message = "Buscando…")
        val found = repository.search(query)
        _state.value = _state.value.copy(
            loading = false,
            results = found,
            message = if (found.isEmpty()) "Sin resultados o sin conexión" else "${found.size} resultados"
        )
    }

    fun clearSearches() = viewModelScope.launch { repository.clearSearches() }

    // ---------- Reproducción ----------

    fun play(song: Song, context: List<Song> = _state.value.results) = viewModelScope.launch {
        val list = context.ifEmpty { listOf(song) }
        val index = list.indexOfFirst { it.id == song.id }.let { if (it >= 0) it else 0 }
        _state.value = _state.value.copy(queue = list, queueIndex = index)
        playCurrent()
    }

    fun playAt(index: Int) = viewModelScope.launch {
        _state.value = _state.value.copy(queueIndex = index)
        playCurrent()
    }

    private suspend fun playCurrent() {
        val queue = _state.value.queue
        val index = _state.value.queueIndex
        if (queue.isEmpty() || index !in queue.indices) return
        val song = queue[index]

        _state.value = _state.value.copy(message = "Obteniendo stream…")
        val stream = repository.resolveStream(song)
        val controller = OndaPlayer.get()
        if (stream == null || controller == null) {
            _state.value = _state.value.copy(message = "No se pudo obtener el audio de esta canción")
            return
        }

        controller.setMediaItem(MediaItem.fromUri(stream))
        controller.prepare()
        controller.playWhenReady = true
        repository.recordPlay(song)
        _state.value = _state.value.copy(
            currentSong = song,
            playing = true,
            position = 0,
            message = "Reproduciendo: ${song.title}"
        )
    }

    fun togglePlay() {
        val controller = OndaPlayer.get() ?: return
        controller.playWhenReady = !controller.playWhenReady
        syncPlayer()
    }

    fun next() = viewModelScope.launch {
        val index = _state.value.queueIndex + 1
        if (index < _state.value.queue.size) {
            playAt(index)
        } else if (_state.value.repeatMode == Player.REPEAT_MODE_ALL && _state.value.queue.isNotEmpty()) {
            playAt(0)
        } else {
            OndaPlayer.get()?.stop()
            _state.value = _state.value.copy(playing = false, position = 0)
        }
    }

    fun previous() = viewModelScope.launch {
        val controller = OndaPlayer.get()
        if (controller != null && controller.currentPosition > 3000) {
            controller.seekTo(0)
            return@launch
        }
        val index = _state.value.queueIndex - 1
        if (index >= 0) {
            playAt(index)
        } else if (_state.value.repeatMode == Player.REPEAT_MODE_ALL && _state.value.queue.isNotEmpty()) {
            playAt(_state.value.queue.lastIndex)
        } else {
            controller?.seekTo(0)
        }
    }

    fun seekTo(ms: Long) {
        OndaPlayer.get()?.seekTo(ms)
        _state.value = _state.value.copy(position = ms)
    }

    fun setShuffle(enabled: Boolean) {
        OndaPlayer.get()?.shuffleModeEnabled = enabled
        syncPlayer()
    }

    fun cycleRepeat() {
        val controller = OndaPlayer.get() ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        syncPlayer()
    }

    fun addNext(song: Song) {
        val queue = _state.value.queue.toMutableList()
        val at = (_state.value.queueIndex + 1).coerceAtMost(queue.size)
        queue.add(at, song)
        _state.value = _state.value.copy(
            queue = queue,
            message = "Siguiente en la cola: ${song.title}"
        )
    }

    fun removeFromQueue(index: Int) {
        if (index !in _state.value.queue.indices) return
        val queue = _state.value.queue.toMutableList()
        queue.removeAt(index)
        val current = _state.value.queueIndex
        val newIndex = when {
            queue.isEmpty() -> -1
            index < current -> current - 1
            index == current -> current.coerceAtMost(queue.lastIndex)
            else -> current
        }
        _state.value = _state.value.copy(queue = queue, queueIndex = newIndex)
        if (index == current && queue.isNotEmpty()) playAt(newIndex)
    }

    // ---------- Me gusta ----------

    fun toggleLike(song: Song) = viewModelScope.launch {
        val liked = song.id !in _state.value.likedIds
        repository.setLiked(song, liked)
        _state.value = _state.value.copy(message = if (liked) "Añadido a Me gusta" else "Quitado de Me gusta")
    }

    fun toggleLikeCurrent() {
        _state.value.currentSong?.let { toggleLike(it) }
    }

    // ---------- DJ ----------

    fun toggleDj() {
        _state.value = _state.value.copy(djEnabled = !_state.value.djEnabled)
    }

    fun djNext() = viewModelScope.launch {
        val next = repository.suggest(_state.value.queue + _state.value.results)
        if (next == null) {
            _state.value = _state.value.copy(message = "Escucha algunas canciones o marca Me gusta para activar el DJ")
        } else {
            play(next, _state.value.queue + listOf(next))
        }
    }

    // ---------- Álbumes y playlists ----------

    fun createAlbum(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val id = repository.createAlbum(name)
        _state.value = _state.value.copy(message = "Álbum creado: $name")
        onCreated(id)
    }

    fun addToAlbum(albumId: Long, song: Song) = viewModelScope.launch {
        repository.addToAlbum(albumId, song)
        _state.value = _state.value.copy(message = "Añadida a álbum: ${song.title}")
    }

    fun removeFromAlbum(albumId: Long, songId: String) = viewModelScope.launch {
        repository.removeFromAlbum(albumId, songId)
        _state.value = _state.value.copy(message = "Canción eliminada del álbum")
    }

    fun deleteAlbum(albumId: Long) = viewModelScope.launch {
        repository.deleteAlbum(albumId)
        _state.value = _state.value.copy(message = "Álbum eliminado")
    }

    fun createPlaylist(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val id = repository.createPlaylist(name)
        _state.value = _state.value.copy(message = "Playlist creada: $name")
        onCreated(id)
    }

    fun renamePlaylist(playlistId: Long, name: String) = viewModelScope.launch {
        repository.renamePlaylist(playlistId, name)
        _state.value = _state.value.copy(message = "Playlist renombrada")
    }

    fun deletePlaylist(playlistId: Long) = viewModelScope.launch {
        repository.deletePlaylist(playlistId)
        _state.value = _state.value.copy(message = "Playlist eliminada")
    }

    fun addToPlaylist(playlistId: Long, song: Song) = viewModelScope.launch {
        repository.addToPlaylist(playlistId, song)
        _state.value = _state.value.copy(
            message = if (playlistId == LIKED_PLAYLIST_ID) "Añadido a Me gusta" else "Añadido a la playlist"
        )
    }

    fun removeFromPlaylist(playlistId: Long, songId: String) = viewModelScope.launch {
        repository.removeFromPlaylist(playlistId, songId)
        _state.value = _state.value.copy(message = "Canción eliminada de la playlist")
    }

    // ---------- Compartir ----------

    fun shareList(name: String, songs: List<Song>) = viewModelScope.launch {
        val payload = repository.exportList(name, songs)
        val bitmap = QrCodec.encode(payload)
        _state.value = _state.value.copy(
            qr = bitmap,
            message = if (bitmap != null) "Muestra este QR a tu amigo" else "QR demasiado grande; usa el texto compartido"
        )
    }

    fun importShared(payload: String) = viewModelScope.launch {
        val imported = repository.importList(payload)
        if (imported == null) {
            _state.value = _state.value.copy(message = "El código compartido no es válido")
            return@launch
        }
        val (name, songs) = imported
        val albumId = repository.createAlbum(name)
        songs.forEach { repository.addToAlbum(albumId, it) }
        _state.value = _state.value.copy(message = "Álbum importado: $name (${songs.size} canciones)")
    }

    // ---------- Actualizaciones ----------

    fun checkUpdate() = viewModelScope.launch {
        val release = Updater.latest()
        _state.value = _state.value.copy(
            updateAvailable = release?.tag,
            message = if (release == null) {
                "No se pudo comprobar actualizaciones"
            } else if (release.tag.trimStart('v') != BuildConfig.VERSION_NAME) {
                "Actualización disponible: ${release.tag}"
            } else {
                "Ya tienes la última versión (${BuildConfig.VERSION_NAME})"
            }
        )
    }

    fun downloadUpdate() = viewModelScope.launch {
        _state.value = _state.value.copy(message = "Descargando actualización…")
        val release = Updater.latest()
        if (release == null) {
            _state.value = _state.value.copy(message = "No hay una release disponible")
            return@launch
        }
        val file = Updater.download(getApplication(), release.apkUrl)
        if (file == null) {
            _state.value = _state.value.copy(message = "Error al descargar la actualización")
        } else {
            Updater.install(getApplication(), file)
            _state.value = _state.value.copy(message = "Instalando ${release.tag}…")
        }
    }

    override fun onCleared() {
        super.onCleared()
        OndaPlayer.disconnect()
    }
}
