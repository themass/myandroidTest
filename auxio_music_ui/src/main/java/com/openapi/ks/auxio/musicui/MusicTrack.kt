package com.openapi.ks.auxio.musicui

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 宿主传入的曲目模型（与业务 VO 解耦）。
 */
@Parcelize
data class MusicTrack(
    val id: Long,
    val title: String,
    val mediaUrl: String,
    val artworkUrl: String? = null,
) : Parcelable
