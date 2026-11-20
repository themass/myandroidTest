package com.openapi.retromusic.interfaces

import android.view.View
import com.openapi.retromusic.db.PlaylistWithSongs

interface IPlaylistClickListener {
    fun onPlaylistClick(playlistWithSongs: PlaylistWithSongs, view: View)
}