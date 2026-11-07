package com.openapi.retromusic.model.smartplaylist

import com.openapi.retromusic.App
import com.openapi.retromusic.R
import com.openapi.retromusic.model.Song
import kotlinx.parcelize.Parcelize

@Parcelize
class NotPlayedPlaylist : AbsSmartPlaylist(
    name = App.getContext().getString(R.string.not_recently_played),
    iconRes = R.drawable.ic_audiotrack
) {
    override fun songs(): List<Song> {
        return topPlayedRepository.notRecentlyPlayedTracks()
    }
}