package com.openapi.feature.user.avatar

import android.content.Context
import com.openapi.common.util.android.PreferenceUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 用户头像的本地指针与文件：`files/avatar/current.jpg` + pref 记路径
 * （`PreferenceUtils` 固定用 `app_preferences` 文件，所以键名自带命名空间）。
 *
 * 「失败不覆盖旧头像」的落点在这里：[installInto] 先把新字节写进 `.tmp`，**写完整了才 move
 * 换掉旧文件**。中途任何失败（磁盘满、进程被杀）旧文件原样在；pref 只在 move 成功后才写。
 *
 * pref 存路径而不是布尔：以后换文件命名（多尺寸、webp）不用动读取方。读取时核文件还存在——
 * 用户可能从系统存储页清掉缓存，这时返回 null，UI 回落到默认头像 drawable。
 *
 * 核心写装逻辑 [installInto] 只接目录（纯文件操作，JVM 单测直接跑 temp 目录），
 * pref 读写是薄壳——这也是刻意的分层。
 */
object UserAvatarStore {

    /** 命名空间前缀：PreferenceUtils 没有多 pref 文件的概念，键名自己分域。 */
    private const val KEY_PATH = "user_avatar_path"
    private const val FILE_NAME = "current.jpg"

    fun dir(context: Context): File = File(context.filesDir, "avatar")

    /** 当前头像的本地文件路径；没有头像或文件已被清掉返回 null。 */
    fun avatarFilePath(context: Context): String? {
        val path = PreferenceUtils.getPrefString(context, KEY_PATH, null) ?: return null
        return if (File(path).exists()) path else null
    }

    /** 安装并把路径写进 pref。返回 false = 旧头像（文件与 pref）原样不动。 */
    fun install(context: Context, bytes: ByteArray): Boolean {
        val target = installInto(dir(context), bytes) ?: return false
        PreferenceUtils.setPrefString(context, KEY_PATH, target.absolutePath)
        return true
    }

    /**
     * 把 [bytes] 原子地装成 [dir]/current.jpg：先 `.tmp` 再 move 覆盖。
     * 返回落盘文件；任何失败返回 null 且**不碰旧文件**。
     */
    fun installInto(dir: File, bytes: ByteArray): File? {
        if (!dir.exists() && !dir.mkdirs()) return null
        val target = File(dir, FILE_NAME)
        val tmp = File(dir, "$FILE_NAME.tmp")
        return try {
            tmp.writeBytes(bytes)
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            target
        } catch (_: Exception) {
            runCatching { tmp.delete() }
            null
        }
    }
}
