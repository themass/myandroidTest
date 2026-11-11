package com.openapi.retromusic.extensions

import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import com.openapi.retromusic.model.Song
import com.openapi.retromusic.util.MusicUtil

/** 播放地址：本地歌曲走 MediaStore 的 content 地址；频道的远程歌曲（data 是 http/https）直接用它。 */
val Song.uri: android.net.Uri
    get() = if (isRemote) android.net.Uri.parse(data) else MusicUtil.getSongFileUri(songId = id)

/** 远程歌曲：地址是 http/https，不在 MediaStore 里。 */
val Song.isRemote: Boolean
    get() = data.startsWith("http://") || data.startsWith("https://")

val Song.albumArtUri get() = MusicUtil.getMediaStoreAlbumCoverUri(albumId)

fun ArrayList<Song>.toMediaSessionQueue(): List<QueueItem> {
    return map { song ->
        val mediaDescription = MediaDescriptionCompat.Builder()
            .setMediaId(song.id.toString())
            .setTitle(song.title)
            .setSubtitle(song.artistName)
            .setIconUri(song.albumArtUri)
            .build()
        QueueItem(mediaDescription, song.hashCode().toLong())
    }
}
