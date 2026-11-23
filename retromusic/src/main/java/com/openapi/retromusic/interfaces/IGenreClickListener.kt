package com.openapi.retromusic.interfaces

import android.view.View
import com.openapi.retromusic.model.Genre

interface IGenreClickListener {
    fun onClickGenre(genre: Genre, view: View)
}