package app.sonora.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY addedAt DESC")
    fun all(): Flow<List<Song>>

    @Query("SELECT * FROM songs")
    suspend fun allNow(): List<Song>

    @Query("SELECT COUNT(*) FROM songs WHERE uri = :uri")
    suspend fun count(uri: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(song: Song): Long

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE songs SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE songs SET favorite = 1 WHERE uri = :uri")
    suspend fun favoriteByUri(uri: String)

    @Query("UPDATE songs SET lastPlayedAt = :t WHERE id = :id")
    suspend fun markPlayed(id: Long, t: Long)

    @Query("UPDATE songs SET title = :title, artist = :artist, album = :album WHERE id = :id")
    suspend fun updateInfo(id: Long, title: String, artist: String, album: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun all(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists")
    suspend fun allNow(): List<Playlist>

    @Query("SELECT * FROM playlist_songs ORDER BY playlistId, position")
    fun allEntries(): Flow<List<PlaylistSong>>

    @Query("SELECT * FROM playlist_songs ORDER BY playlistId, position")
    suspend fun entriesNow(): List<PlaylistSong>

    @Insert
    suspend fun insert(p: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addEntry(e: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :pid AND songId = :sid")
    suspend fun removeEntry(pid: Long, sid: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :pid")
    suspend fun nextPosition(pid: Long): Int

    @Query("UPDATE playlist_songs SET position = :pos WHERE playlistId = :pid AND songId = :sid")
    suspend fun setPosition(pid: Long, sid: Long, pos: Int)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `lastPlayedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_songs` (`playlistId` INTEGER NOT NULL, `songId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`playlistId`, `songId`), " +
                "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_songId` ON `playlist_songs` (`songId`)")
    }
}

@Dao
interface LinkDao {
    @Query("SELECT * FROM links ORDER BY addedAt DESC")
    fun all(): Flow<List<LinkRef>>

    @Query("SELECT * FROM links")
    suspend fun allNow(): List<LinkRef>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(l: LinkRef): Long

    @Query("DELETE FROM links WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM playlist_links ORDER BY playlistId, position")
    fun allEntries(): Flow<List<PlaylistLink>>

    @Query("SELECT * FROM playlist_links ORDER BY playlistId, position")
    suspend fun entriesNow(): List<PlaylistLink>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addEntry(e: PlaylistLink)

    @Query("DELETE FROM playlist_links WHERE playlistId = :pid AND linkId = :lid")
    suspend fun removeEntry(pid: Long, lid: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_links WHERE playlistId = :pid")
    suspend fun nextPosition(pid: Long): Int
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `links` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, `service` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT, `thumbnail` TEXT, `addedAt` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_links_url` ON `links` (`url`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_links` (`playlistId` INTEGER NOT NULL, `linkId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`playlistId`, `linkId`), " +
                "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`linkId`) REFERENCES `links`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_links_linkId` ON `playlist_links` (`linkId`)")
    }
}

@Database(entities = [Song::class, Playlist::class, PlaylistSong::class, LinkRef::class, PlaylistLink::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun linkDao(): LinkDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "sonora.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
