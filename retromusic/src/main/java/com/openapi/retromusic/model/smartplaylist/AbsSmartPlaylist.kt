package com.openapi.retromusic.model.smartplaylist

import androidx.annotation.DrawableRes
import com.openapi.retromusic.R
import com.openapi.retromusic.model.AbsCustomPlaylist

abstract class AbsSmartPlaylist(
    name: String,
    @DrawableRes val iconRes: Int = R.drawable.ic_queue_music
) : AbsCustomPlaylist(
    id = PlaylistIdGenerator(name, iconRes),
    name = name
)