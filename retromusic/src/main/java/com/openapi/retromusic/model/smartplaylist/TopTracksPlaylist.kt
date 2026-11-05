package com.openapi.retromusic.model.smartplaylist

import com.openapi.retromusic.App
import com.openapi.retromusic.R
import com.openapi.retromusic.model.Song
import kotlinx.parcelize.Parcelize

@Parcelize
class TopTracksPlaylist : AbsSmartPlaylist(
    name = App.getContext().getString(R.string.my_top_tracks),
    iconRes = R.drawable.ic_trending_up
) {
    override fun songs(): List<Song> {
        return topPlayedRepository.topTracks()
    }
}