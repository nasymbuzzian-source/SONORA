package app.sonora.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File

@Entity(tableName = "songs", indices = [Index(value = ["uri"], unique = true)])
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverPath: String?,
    val addedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val lastPlayedAt: Long = 0
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Song::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["songId"])]
)
data class PlaylistSong(val playlistId: Long, val songId: Long, val position: Int)

@Entity(tableName = "links", indices = [Index(value = ["url"], unique = true)])
data class LinkRef(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val service: String,
    val kind: String,
    val title: String,
    val artist: String?,
    val thumbnail: String?,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_links",
    primaryKeys = ["playlistId", "linkId"],
    foreignKeys = [
        ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LinkRef::class, parentColumns = ["id"], childColumns = ["linkId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["linkId"])]
)
data class PlaylistLink(val playlistId: Long, val linkId: Long, val position: Int)

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(coverPath?.let { Uri.fromFile(File(it)) })
            .build()
    )
    .build()
