package com.openapi.retromusic.glide.artistimage

import com.openapi.retromusic.model.Artist

class ArtistImage(val artist: Artist){
    override fun equals(other: Any?): Boolean {
        if (other is ArtistImage){
            return other.artist == artist
        }
        return false
    }

    override fun hashCode(): Int {
        return artist.hashCode()
    }
}