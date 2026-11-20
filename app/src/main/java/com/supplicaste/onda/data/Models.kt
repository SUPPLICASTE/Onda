package com.supplicaste.onda.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val artwork: String,
    val sourceUrl: String
)

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

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val playedAt: Long = System.currentTimeMillis(),
    val completed: Boolean = true
)

@Dao
interface OndaDao {
    @Query("SELECT * FROM albums ORDER BY createdAt DESC")
    fun albums(): Flow<List<AlbumEntity>>

    @Query("SELECT s.* FROM songs s INNER JOIN album_songs a ON a.songId = s.id WHERE a.albumId = :albumId ORDER BY a.position")
    fun songsInAlbum(albumId: Long): Flow<List<SongEntity>>

    @Query("SELECT s.* FROM songs s INNER JOIN album_songs a ON a.songId = s.id WHERE a.albumId = :albumId ORDER BY a.position")
    suspend fun songsInAlbumOnce(albumId: Long): List<SongEntity>

    @Query("SELECT DISTINCT s.* FROM songs s INNER JOIN album_songs a ON a.songId = s.id")
    suspend fun allSavedSongs(): List<SongEntity>

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
    suspend fun insertHistory(history: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 300")
    suspend fun recentHistory(): List<HistoryEntity>
}

@Database(
    entities = [SongEntity::class, AlbumEntity::class, AlbumSongEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OndaDatabase : RoomDatabase() {
    abstract fun dao(): OndaDao
}
