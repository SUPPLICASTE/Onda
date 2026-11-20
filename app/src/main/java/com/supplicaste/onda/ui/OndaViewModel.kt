package com.supplicaste.onda.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.supplicaste.onda.BuildConfig
import com.supplicaste.onda.auth.AuthManager
import com.supplicaste.onda.data.AlbumEntity
import com.supplicaste.onda.data.OndaDatabase
import com.supplicaste.onda.data.OndaRepository
import com.supplicaste.onda.data.Song
import com.supplicaste.onda.player.OndaPlayer
import com.supplicaste.onda.share.QrCodec
import com.supplicaste.onda.updater.Updater
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OndaUiState(
    val email: String? = null,
    val authMode: String = "",
    val loading: Boolean = false,
    val message: String = "",
    val results: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val playing: Boolean = false,
    val djEnabled: Boolean = false,
    val qr: Bitmap? = null,
    val updateAvailable: String? = null
)

class OndaViewModel(app: Application) : AndroidViewModel(app) {
    private val database = Room.databaseBuilder(app, OndaDatabase::class.java, "onda.db").build()
    private val repository = OndaRepository(database.dao())
    private val auth = AuthManager(app)

    private val _state = MutableStateFlow(
        OndaUiState(
            email = auth.currentEmail(),
            authMode = auth.modeLabel
        )
    )
    val state: StateFlow<OndaUiState> = _state.asStateFlow()
    val albums = repository.albums

    init {
        viewModelScope.launch {
            OndaBus.imported.collect { code -> importShared(code) }
        }
    }

    fun songsOf(albumId: Long) = repository.songsInAlbum(albumId)

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
        OndaPlayer.release()
        _state.value = OndaUiState(authMode = auth.modeLabel, message = "Sesión cerrada")
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

    fun play(song: Song) = viewModelScope.launch {
        _state.value = _state.value.copy(message = "Obteniendo stream…")
        val stream = repository.resolveStream(song)
        if (stream == null) {
            _state.value = _state.value.copy(message = "No se pudo obtener el audio de esta canción")
            return@launch
        }
        OndaPlayer.play(getApplication(), stream)
        repository.recordPlay(song)
        _state.value = _state.value.copy(
            currentSong = song,
            playing = true,
            message = "Reproduciendo: ${song.title}"
        )
    }

    fun togglePlay() {
        OndaPlayer.toggle()
        _state.value = _state.value.copy(playing = OndaPlayer.isPlaying())
    }

    fun toggleDj() {
        _state.value = _state.value.copy(djEnabled = !_state.value.djEnabled)
    }

    fun djNext() = viewModelScope.launch {
        val next = repository.suggest(_state.value.results)
        if (next == null) {
            _state.value = _state.value.copy(message = "Escucha algunas canciones para activar el DJ")
        } else {
            play(next)
        }
    }

    fun createAlbum(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val id = repository.createAlbum(name)
        _state.value = _state.value.copy(message = "Álbum creado: $name")
        onCreated(id)
    }

    fun addToAlbum(albumId: Long, song: Song) = viewModelScope.launch {
        val current = database.dao().songsInAlbumOnce(albumId).size
        repository.addToAlbum(albumId, song, current)
        _state.value = _state.value.copy(message = "Añadida: ${song.title}")
    }

    fun removeFromAlbum(albumId: Long, songId: String) = viewModelScope.launch {
        repository.removeFromAlbum(albumId, songId)
        _state.value = _state.value.copy(message = "Canción eliminada del álbum")
    }

    fun deleteAlbum(albumId: Long) = viewModelScope.launch {
        repository.deleteAlbum(albumId)
        _state.value = _state.value.copy(message = "Álbum eliminado")
    }

    fun shareAlbum(album: AlbumEntity) = viewModelScope.launch {
        val payload = repository.exportAlbum(album.name)
        val bitmap = QrCodec.encode(payload)
        _state.value = _state.value.copy(
            qr = bitmap,
            message = if (bitmap != null) "Muestra este QR a tu amigo" else "QR demasiado grande; usa el texto compartido"
        )
    }

    fun importShared(payload: String) = viewModelScope.launch {
        val imported = repository.importAlbum(payload)
        if (imported == null) {
            _state.value = _state.value.copy(message = "El código compartido no es válido")
            return@launch
        }
        val (name, songs) = imported
        val albumId = repository.createAlbum(name)
        songs.forEachIndexed { index, song -> repository.addToAlbum(albumId, song, index) }
        _state.value = _state.value.copy(message = "Álbum importado: $name (${songs.size} canciones)")
    }

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
        OndaPlayer.release()
    }
}
