package com.openapi.retromusic.glide

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.openapi.retromusic.glide.artistimage.ArtistImage
import com.openapi.retromusic.glide.artistimage.Factory
import com.openapi.retromusic.glide.audiocover.AudioFileCover
import com.openapi.retromusic.glide.audiocover.AudioFileCoverLoader
import com.openapi.retromusic.glide.palette.BitmapPaletteTranscoder
import com.openapi.retromusic.glide.palette.BitmapPaletteWrapper
import com.openapi.retromusic.glide.playlistPreview.PlaylistPreview
import com.openapi.retromusic.glide.playlistPreview.PlaylistPreviewLoader
import java.io.InputStream

/**
 * 本地音乐用到的 Glide 组件（播放列表预览、专辑封面、艺人图）。
 *
 * 原先是 @GlideModule 的 AppGlideModule，但宿主 app 已经由 commonutil 生成了
 * GeneratedAppGlideModuleImpl，两个 AppGlideModule 会在 dex 合并时冲突。这里改成普通函数，
 * 由 [com.openapi.retromusic.App.init] 在启动时注册进宿主的 Glide 实例。
 */
object RetroMusicGlide {

    fun register(context: Context) {
        registerComponents(context, Glide.get(context).registry)
    }

    private fun registerComponents(context: Context, registry: Registry) {
        registry.prepend(
            PlaylistPreview::class.java,
            Bitmap::class.java,
            PlaylistPreviewLoader.Factory(context)
        )
        registry.prepend(
            AudioFileCover::class.java,
            InputStream::class.java,
            AudioFileCoverLoader.Factory()
        )
        registry.prepend(ArtistImage::class.java, InputStream::class.java, Factory(context))
        registry.register(
            Bitmap::class.java,
            BitmapPaletteWrapper::class.java, BitmapPaletteTranscoder()
        )
    }
}
