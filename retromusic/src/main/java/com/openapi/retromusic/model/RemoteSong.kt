package com.openapi.retromusic.model

/**
 * 远程歌曲（频道的音频地址）。它不在 MediaStore 里，所以 id 用地址的哈希取负数，
 * 避开 MediaStore 的正数 id 和本地的 -1。
 */
fun remoteSong(url: String, title: String, artist: String, durationMs: Long): Song {
    val id = -2L - (url.hashCode().toLong() and 0x7fffffffL)
    return Song(
        id = id,
        title = title,
        trackNumber = 0,
        year = 0,
        duration = durationMs,
        data = url,
        dateModified = 0L,
        albumId = -1L,
        albumName = "",
        artistId = -1L,
        artistName = artist,
        composer = null,
        albumArtist = null,
    )
}
