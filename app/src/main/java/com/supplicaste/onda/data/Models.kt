package com.supplicaste.onda.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

const val LIKED_PLAYLIST_ID = -1L

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val artwork: String,
    val sourceUrl: String
)

data class ArtistCount(val artist: String, val plays: Int)

data class OndaStats(val liked: Int, val albums: Int, val playlists: Int, val plays: Int)

fun SongEntity.toSong() = Song(id, title, artist, durationSec, artwork, sourceUrl)

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val artwork: String,
    val sourceUrl: String
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "album_songs", primaryKeys = ["albumId", "songId"])
data class AlbumSongEntity(
    val albumId: Long,
    val songId: String,
    val position: Int
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: String,
    val position: Int
)

@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val songId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val playedAt: Long = System.currentTimeMillis(),
    val completed: Boolean = true
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

@Dao
interface OndaDao {
    @Query("SELECT * FROM albums ORDER BY createdAt DESC")
    fun albums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT s.* FROM songs s INNER JOIN album_songs a ON a.songId = s.id WHERE a.albumId = :albumId ORDER BY a.position")
    fun songsInAlbum(albumId: Long): Flow<List<SongEntity>>

    @Query("SELECT s.* FROM songs s INNER JOIN album_songs a ON a.songId = s.id WHERE a.albumId = :albumId ORDER BY a.position")
    suspend fun songsInAlbumOnce(albumId: Long): List<SongEntity>

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs p ON p.songId = s.id WHERE p.playlistId = :playlistId ORDER BY p.position")
    fun songsInPlaylist(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs p ON p.songId = s.id WHERE p.playlistId = :playlistId ORDER BY p.position")
    suspend fun songsInPlaylistOnce(playlistId: Long): List<SongEntity>

    @Query("SELECT s.* FROM songs s INNER JOIN liked_songs l ON l.songId = s.id ORDER BY l.addedAt DESC")
    fun likedSongs(): Flow<List<SongEntity>>

    @Query("SELECT s.* FROM songs s INNER JOIN liked_songs l ON l.songId = s.id ORDER BY l.addedAt DESC")
    suspend fun likedSongsOnce(): List<SongEntity>

    @Query("SELECT songId FROM liked_songs")
    fun likedIds(): Flow<List<String>>

    @Query("SELECT s.* FROM history h INNER JOIN songs s ON s.id = h.songId GROUP BY s.id ORDER BY MAX(h.playedAt) DESC LIMIT :limit")
    fun recentSongs(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT s.artist AS artist, COUNT(*) AS plays FROM history h INNER JOIN songs s ON s.id = h.songId GROUP BY s.artist ORDER BY plays DESC LIMIT :limit")
    fun topArtists(limit: Int): Flow<List<ArtistCount>>

    @Query("SELECT (SELECT COUNT(*) FROM liked_songs) AS liked, (SELECT COUNT(*) FROM albums) AS albums, (SELECT COUNT(*) FROM playlists) AS playlists, (SELECT COUNT(*) FROM history) AS plays")
    fun stats(): Flow<OndaStats>

    @Query("SELECT * FROM songs")
    suspend fun allSongs(): List<SongEntity>

    @Query("SELECT query FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun recentSearches(limit: Int): Flow<List<String>>

    @Insert
    suspend fun insertSearch(search: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearches()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSong(song: SongEntity)

    @Insert
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumSong(link: AlbumSongEntity)

    @Query("DELETE FROM album_songs WHERE albumId = :albumId AND songId = :songId")
    suspend fun removeFromAlbum(albumId: Long, songId: String)

    @Query("DELETE FROM album_songs WHERE albumId = :albumId")
    suspend fun clearAlbum(albumId: Long)

    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteAlbum(albumId: Long)

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(link: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeFromPlaylist(playlistId: Long, songId: String)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiked(liked: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE songId = :songId")
    suspend fun deleteLiked(songId: String)

    @Insert
    suspend fun insertHistory(history: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 300")
    suspend fun recentHistory(): List<HistoryEntity>

    @Query("SELECT DISTINCT songId FROM playlist_songs UNION SELECT DISTINCT songId FROM album_songs")
    suspend fun collectionSongIds(): List<String>
}

@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        AlbumSongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        LikedSongEntity::class,
        HistoryEntity::class,
        SearchHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class OndaDatabase : RoomDatabase() {
    abstract fun dao(): OndaDao
}

/** Migración incremental: conserva álbumes, canciones e historial de la v1. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_songs` (`playlistId` INTEGER NOT NULL, `songId` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `songId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `liked_songs` (`songId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `query` TEXT NOT NULL, `searchedAt` INTEGER NOT NULL)")
    }
}
