package com.openapi.ks.myapp.avatar

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * 已安装形象包的本地仓库：``files/avatars/{avatar_key}/v{version}/``，原子安装（先解压到临时目录再改名），
 * 旧版本在新版本装好后删除。只存在已校验过的包；校验失败的下载不会留下任何痕迹。
 *
 * 另外记一份「角色 → 形象」的索引（``index.json``），离线时也能知道某个角色上次用的是哪个形象。
 */
class AvatarPackStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "avatars").apply { mkdirs() }
    private val indexFile = File(root, "index.json")

    class Corrupt(message: String) : Exception(message)

    fun installedVersion(avatarKey: String): Int? =
        dir(avatarKey).listFiles()?.mapNotNull { it.name.removePrefix("v").toIntOrNull() }?.maxOrNull()

    /** 已安装的最新版本的素材；没装或坏了返回 null。 */
    fun load(avatarKey: String): AvatarFiles? {
        val version = installedVersion(avatarKey) ?: return null
        val versionDir = File(dir(avatarKey), "v$version")
        return runCatching {
            AvatarManifest.parse(File(versionDir, "manifest.json").readText(), versionDir)
        }.getOrNull()
    }

    fun avatarFor(roleId: String): String? = readIndex().optString(roleId).takeIf { it.isNotEmpty() }

    fun bind(roleId: String, avatarKey: String) {
        val index = readIndex().put(roleId, avatarKey)
        indexFile.writeText(index.toString())
    }

    /**
     * 校验 sha256、解压并安装。任何一步失败都抛异常，已安装的版本保持不变。
     * [source] 由调用方关闭。
     */
    fun install(avatarKey: String, version: Int, expectedSha256: String, zip: File) {
        require(KEY.matches(avatarKey)) { "avatar_key 不合法" }
        if (sha256(zip) != expectedSha256.lowercase()) throw Corrupt("校验值不一致")
        val target = File(dir(avatarKey), "v$version")
        val staging = File(dir(avatarKey), ".staging-v$version-${System.nanoTime()}").apply { mkdirs() }
        try {
            zip.inputStream().use { unzip(it, staging) }
            AvatarManifest.parse(File(staging, "manifest.json").readText(), staging)   // 装之前先确认包是完整的
            target.deleteRecursively()
            if (!staging.renameTo(target)) throw Corrupt("无法安装形象包")
        } finally {
            staging.deleteRecursively()
        }
        // 新版本就位后再清理旧版本，避免装到一半没有可用的形象
        dir(avatarKey).listFiles()?.filter { it.isDirectory && it.name != "v$version" && !it.name.startsWith(".") }
            ?.forEach { it.deleteRecursively() }
    }

    fun remove(avatarKey: String) {
        dir(avatarKey).deleteRecursively()
    }

    private fun dir(avatarKey: String): File = File(root, avatarKey)

    private fun readIndex(): JSONObject =
        runCatching { JSONObject(indexFile.readText()) }.getOrDefault(JSONObject())

    private fun unzip(input: InputStream, into: File) {
        val base = into.canonicalPath + File.separator
        var entries = 0
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                if (++entries > MAX_ENTRIES) throw Corrupt("文件过多")
                val out = File(into, entry.name).canonicalFile
                if (!out.path.startsWith(base)) throw Corrupt("包里有越界路径")   // zip-slip
                out.parentFile?.mkdirs()
                out.outputStream().use { sink ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_TOTAL_BYTES) throw Corrupt("解压后过大")
                        sink.write(buf, 0, n)
                    }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val KEY = Regex("^[a-z0-9][a-z0-9_\\-]{0,63}$")
        const val MAX_ENTRIES = 200
        const val MAX_TOTAL_BYTES = 20L * 1024 * 1024   // 与服务端上限一致
    }
}
