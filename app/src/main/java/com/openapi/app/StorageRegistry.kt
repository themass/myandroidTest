package com.openapi.app

import android.content.Context
import com.openapi.common.storage.StorageCatalog
import com.openapi.common.storage.StorageCatalog.Entry.Root
import com.openapi.common.storage.StorageDirs

/**
 * 把各模块的目录名翻译成用户能读懂的条目。**只在 :app 调用一次**。
 *
 * 为什么放在 :app：它是组合根，唯一允许同时知道所有模块的地方。
 * `:common` 里的 [StorageCatalog] 只提供登记和统计能力，不认识任何业务；
 * 各 feature 模块只暴露目录名（[StorageDirs]），不依赖设置页。
 *
 * 登记的内容就是设置页「存储管理」里展示的列表，所以描述要写给用户看：
 * 说清楚「里面是什么」「删了会怎样」，不要写实现术语。
 */
object StorageRegistry {

    private var installed = false

    /** 幂等：多次调用不会重复登记。 */
    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true

        // ---- 用户数据（删除要确认）----
        StorageCatalog.register(
            StorageCatalog.Entry(
                dirName = StorageDirs.PHOTOSOLVE_HISTORY,
                root = Root.FILES,
                title = "解题历史",
                description = "拍照解题的题目和解答记录。删除后历史记录无法恢复。",
                userData = true,
            ),
        )
        StorageCatalog.register(
            StorageCatalog.Entry(
                dirName = StorageDirs.AVATARS,
                root = Root.FILES,
                title = "形象资源",
                description = "陪伴页的 2D 形象素材。删除后会重新下载。",
                userData = false,
            ),
        )
        StorageCatalog.register(
            StorageCatalog.Entry(
                dirName = StorageDirs.CHAT_ATTACHMENTS,
                root = Root.FILES,
                title = "对话附件",
                description = "聊天里发送过的图片和文件缓存。删除不影响已发送的消息。",
                userData = false,
            ),
        )
        StorageCatalog.register(
            StorageCatalog.Entry(
                dirName = StorageDirs.CHAT_CAMERA,
                root = Root.FILES,
                title = "对话拍照",
                description = "聊天里拍下的原始照片。删除不影响已发送的消息。",
                userData = false,
            ),
        )
        StorageCatalog.register(
            StorageCatalog.Entry(
                dirName = StorageDirs.CUSTOM_ARTIST_IMAGES,
                root = Root.FILES,
                title = "歌手图片",
                description = "本地音乐里为歌手设置的封面。删除后恢复默认封面。",
                userData = true,
            ),
        )

        // ---- 缓存（可直接清）----
        StorageCatalog.register(
            StorageCatalog.Entry(
                dirName = StorageDirs.CHAT_TTS,
                root = Root.FILES,
                title = "语音合成缓存",
                description = "朗读回复时生成的音频，可随时清理。",
                userData = false,
            ),
        )
        StorageCatalog.register(
            StorageCatalog.Entry(
                dirName = StorageDirs.PHOTO_EDIT,
                root = Root.CACHE,
                title = "图片编辑结果",
                description = "编辑后待发送的图片，发送完或取消后就没用了。",
                userData = false,
            ),
        )
        StorageCatalog.register(
            StorageCatalog.Entry(
                dirName = StorageDirs.SHARE,
                root = Root.CACHE,
                title = "分享临时文件",
                description = "分享图片时产生的临时文件。",
                userData = false,
            ),
        )
        StorageCatalog.register(
            StorageCatalog.Entry(
                dirName = StorageDirs.AGENT_EXPORT,
                root = Root.CACHE,
                title = "数据导出文件",
                description = "导出聊天记录时生成的临时文件。",
                userData = false,
            ),
        )
    }
}
