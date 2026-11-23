package com.openapi.retromusic.interfaces

import com.openapi.retromusic.model.Album
import com.openapi.retromusic.model.Artist
import com.openapi.retromusic.model.Genre

interface IHomeClickListener {
    fun onAlbumClick(album: Album)

    fun onArtistClick(artist: Artist)

    fun onGenreClick(genre: Genre)
}