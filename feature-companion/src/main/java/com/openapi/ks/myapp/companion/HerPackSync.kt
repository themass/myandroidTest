package com.openapi.ks.myapp.companion

import android.util.Log
import com.openapi.ks.myapp.avatar.CharacterAssetManifest
import com.openapi.ks.myapp.avatar.CharacterAssetPackStore
import com.openapi.ks.myapp.avatar.CharacterAssetPolicy
import com.openapi.ks.myapp.avatar.CharacterEngineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * her 素材包（`model_ref`）在端上的状态：详情抽屉的下载按钮就按这五个状态画，
 * 除 [Installed] 外都可重试。[Downloading.progress] 为 null 表示服务端没给
 * Content-Length——只画转圈，不编百分比。
 */
sealed interface HerPackState {
    data object NotInstalled : HerPackState
    data class Downloading(val progress: Float?) : HerPackState
    data object Installing : HerPackState
    data class Installed(val version: Int) : HerPackState

    /** sha256 对不上，或包内容端上用不了：已装版本不受任何影响，用户可重试。 */
    data class VerifyFailed(val message: String) : HerPackState
}

/** 点下载前该做什么——纯决策，单测钉死（架构纪律：UI 只转发）。 */
enum class HerPackAvailability { DOWNLOAD, SKIP_INSTALLED, UNAVAILABLE }

/**
 * 已装版本 ≥ `pack_version`（即 `model_ref.version`）就跳过——不发请求、不落临时文件；
 * 否则链接 / 哈希 / 版本号三者合格才下载（[HerModelRef.downloadable]），任一不合格就是「暂不可下载」。
 */
fun herPackAvailability(installedVersion: Int?, model: HerModelRef): HerPackAvailability {
    if (installedVersion != null && installedVersion >= model.version) return HerPackAvailability.SKIP_INSTALLED
    return if (model.downloadable) HerPackAvailability.DOWNLOAD else HerPackAvailability.UNAVAILABLE
}

/** renderer → 素材包契约里的引擎类型；认不出回 null（这包端上不知道拿来干什么）。 */
fun herEngineForRenderer(renderer: String): CharacterEngineType? =
    when (renderer.trim().lowercase()) {
        "simple" -> CharacterEngineType.LAYERED
        "live2d" -> CharacterEngineType.LIVE2D
        "rive" -> CharacterEngineType.RIVE
        "duix" -> CharacterEngineType.DUIX
        "tao" -> CharacterEngineType.TAO
        else -> null
    }

/** 下载按钮文案（含「暂不可下载」——download_url 空 / 哈希不合格就说这个，不画可点的按钮）。 */
fun herDownloadButtonLabel(state: HerPackState, downloadable: Boolean): String = when (state) {
    is HerPackState.Installed -> "已安装 v${state.version}"
    is HerPackState.Downloading -> state.progress?.let { "下载中 ${it.roundPercent()}%" } ?: "下载中…"
    HerPackState.Installing -> "安装中…"
    is HerPackState.VerifyFailed -> "校验失败，重试"
    HerPackState.NotInstalled -> if (downloadable) "下载形象包" else "暂不可下载"
}

/** 下载中 / 安装中 / 已装最新 → 按钮禁点；未装或校验失败 → 可重试（前提仍是可下载）。 */
fun herDownloadButtonEnabled(state: HerPackState, downloadable: Boolean): Boolean = when (state) {
    is HerPackState.Installed -> false
    is HerPackState.Downloading, HerPackState.Installing -> false
    HerPackState.NotInstalled, is HerPackState.VerifyFailed -> downloadable
}

private fun Float.roundPercent(): Int = (this * 100f + 0.5f).toInt()

/** 「约 1.2 MB」；服务端没报 size_bytes（0/缺失）就不显示，不编大小。 */
fun herPackSizeLabel(bytes: Long): String? = when {
    bytes <= 0L -> null
    bytes >= 1_000_000L -> "约 %.1f MB".format(java.util.Locale.US, bytes / 1_000_000.0)
    else -> "约 ${bytes / 1000} KB"
}

/** 「2 个特效 · 3 张贴纸」；两边都空就整行不显示（与列表页 herMetaLine 同一口径）。 */
fun herDecorationsLine(effects: List<HerAssetItem>, stickers: List<HerAssetItem>): String? {
    val parts = listOfNotNull(
        effects.size.takeIf { it > 0 }?.let { "$it 个特效" },
        stickers.size.takeIf { it > 0 }?.let { "$it 张贴纸" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** 装饰的名字清单（缺 name 退回 id，不编名字）；只画前 [limit] 个，多了页面上说不完。 */
fun herDecorationNames(detail: HerDetail, limit: Int = 12): List<String> =
    (detail.effects + detail.stickers)
        .map { it.name.trim().takeIf { n -> n.isNotEmpty() } ?: it.id.trim() }
        .filter { it.isNotEmpty() }
        .take(limit)

/**
 * her 包下载 → 校验 → 安装：[CharacterAssetPackStore] 的第一个生产调用方。
 *
 * 四条硬口径（与 [com.openapi.ks.myapp.avatar.AvatarSync] 同款失败姿态）：
 * - 签名链接本身就是授权：下载请求**从不带**登录凭据（对象存储不是我们的 API）；
 * - 整包 sha256 必须等于 `model_ref.hash`，对不上就装都不装（[HerPackState.VerifyFailed]），
 *   已装版本原封不动——[CharacterAssetPackStore] 的原子安装因此永远只见可信包；
 * - `installed >= model_ref.version` 直接跳过，不发请求；
 * - 任何失败都不抛到 UI：回「当前可用」的状态（装了旧版就还是 Installed(旧)），可重试，页面不受影响。
 *
 * zip 的内部布局不在这一刀的服务端契约里，所以 [CharacterAssetManifest] 由包内条目清单
 * **推导**：信任根是「整包 sha256 == 签名 JSON 里的 hash」；路径安全仍由 [CharacterAssetPolicy]
 * 的白名单兜（zip-slip 在 policy 这层就被挡），引擎类型从 `renderer` 映射（[herEngineForRenderer]）。
 */
class HerPackSync(
    /** [CharacterAssetPackStore] 的 filesDir；包落在 filesDir/character-assets/{herId}/v{version}。 */
    private val filesDir: File,
    private val cacheDir: File,
    private val client: OkHttpClient = defaultClient(),
) {
    private val store = CharacterAssetPackStore(filesDir)
    private val packRoot = File(filesDir, "character-assets")

    /**
     * 已装最高版本（不联网）。id 不符合包名形状（服务端坏数据）= 没装过——
     * 也绝不拿它去拼目录，路径注入在门口就挡掉。
     */
    fun installedVersion(herId: String): Int? {
        if (!HER_ID.matches(herId)) return null
        return packRoot.resolve(herId).listFiles()
            ?.mapNotNull { it.name.removePrefix("v").toIntOrNull() }
            ?.maxOrNull()
    }

    /** 详情抽屉刚打开时（不联网、不下载）的按钮状态。 */
    fun stateFor(detail: HerDetail): HerPackState {
        val installed = installedVersion(detail.id)
        return if (installed != null && herPackAvailability(installed, detail.model) == HerPackAvailability.SKIP_INSTALLED) {
            HerPackState.Installed(installed)
        } else {
            HerPackState.NotInstalled
        }
    }

    /**
     * 下载 → 校验 → 安装。[onState] 在过程中回 [HerPackState.Downloading] /
     * [HerPackState.Installing]；返回最终状态。永不抛（见类注释）。
     */
    suspend fun sync(detail: HerDetail, onState: (HerPackState) -> Unit = {}): HerPackState =
        withContext(Dispatchers.IO) {
            // her id 必须符合包名形状（服务端坏数据 / 路径注入）：不碰磁盘也不碰网络。
            if (!HER_ID.matches(detail.id)) return@withContext HerPackState.NotInstalled
            val installed = installedVersion(detail.id)
            when (herPackAvailability(installed, detail.model)) {
                HerPackAvailability.SKIP_INSTALLED -> HerPackState.Installed(installed!!)
                HerPackAvailability.UNAVAILABLE -> HerPackState.NotInstalled
                HerPackAvailability.DOWNLOAD -> runCatching { downloadVerifyInstall(detail, onState) }
                    .getOrElse { error ->
                        // 意外异常也不穿到 UI：保持本地版本，只留日志。
                        Log.w(TAG, "her 包同步异常，保持本地版本 ${detail.id}: ${error.message}")
                        installed?.let { HerPackState.Installed(it) } ?: HerPackState.NotInstalled
                    }
            }
        }

    private fun downloadVerifyInstall(detail: HerDetail, onState: (HerPackState) -> Unit): HerPackState {
        val model = detail.model
        val tmp = File.createTempFile("her-pack-", ".zip", cacheDir)
        try {
            // 签名链接本身就是授权：不要把登录凭据带给对象存储。
            val request = Request.Builder().url(model.downloadUrl.trim()).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("下载失败 HTTP ${response.code}")
                val body = response.body ?: throw IOException("空响应")
                val total = body.contentLength().takeIf { it > 0L }
                if (total != null && total > MAX_DOWNLOAD_BYTES) {
                    throw IOException("包超过端上上限（${total / 1000} KB）")
                }
                body.byteStream().use { input ->
                    tmp.outputStream().use { sink ->
                        val buffer = ByteArray(8192)
                        var done = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            done += n
                            if (done > MAX_DOWNLOAD_BYTES) throw IOException("包超过端上上限")
                            sink.write(buffer, 0, n)
                            if (total != null) {
                                onState(HerPackState.Downloading((done.toFloat() / total).coerceIn(0f, 1f)))
                            }
                        }
                    }
                }
            }
            if (sha256(tmp) != model.hash.trim().lowercase()) {
                Log.i(TAG, "her 包 sha256 与 model_ref.hash 不一致: ${detail.id}")
                return HerPackState.VerifyFailed("校验值不一致")
            }
            val manifest = manifestFromZip(detail, tmp)
                ?: return HerPackState.VerifyFailed("包内容端上用不了（引擎不认识或文件清单不合法）")
            onState(HerPackState.Installing)
            installFromZip(manifest, tmp)
            Log.i(TAG, "her 包就位: ${detail.id} v${manifest.version}")
            return HerPackState.Installed(manifest.version)
        } finally {
            tmp.delete()
        }
    }

    /**
     * 从 zip 条目清单推导 [CharacterAssetManifest]：schema 1、packId = her id、
     * version = `model_ref.version`、entry_point 优先用 `model_ref.asset`（包里真有它的话）。
     * 引擎不认识 / 空包 / 路径过不了 policy 白名单，都回 null（= [HerPackState.VerifyFailed]）。
     */
    private fun manifestFromZip(detail: HerDetail, zip: File): CharacterAssetManifest? {
        val engine = herEngineForRenderer(detail.renderer) ?: return null
        return runCatching {
            ZipFile(zip).use { archive ->
                val files = LinkedHashMap<String, String>()
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || entry.name in files) continue
                    archive.getInputStream(entry).use { files[entry.name] = sha256Stream(it) }
                }
                if (files.isEmpty()) return@use null
                val entryPoint = detail.model.asset.trim().takeIf { it.isNotEmpty() && it in files }
                    ?: files.keys.sorted().first()
                CharacterAssetManifest(1, detail.id, detail.model.version, engine, entryPoint, files)
            }
        }.getOrNull()?.takeIf { CharacterAssetPolicy.isValid(it) }
    }

    /** 安装期间 zip 必须留在原地（source 按条目名重开流）；装完在调用方 finally 删。 */
    private fun installFromZip(manifest: CharacterAssetManifest, zip: File) {
        ZipFile(zip).use { archive ->
            store.install(manifest) { path ->
                archive.getEntry(path)?.let { archive.getInputStream(it) }
                    ?: throw IOException("包里缺文件 $path")
            }
        }
    }

    private fun sha256(file: File): String = file.inputStream().use { sha256Stream(it) }

    private fun sha256Stream(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "HerPackSync"

        /** 与 CharacterAssetPackStore 的安装上限同数：超限在下载阶段就断，不落一个注定装不下的临时文件。 */
        const val MAX_DOWNLOAD_BYTES = 100L * 1024 * 1024

        /** [CharacterAssetPolicy] 的 packId 形状（那边是私有的，这里按同一口径钉一份）。 */
        val HER_ID = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

        /** 包比 JSON 接口大得多：读超时给足，连接超时照旧。 */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
