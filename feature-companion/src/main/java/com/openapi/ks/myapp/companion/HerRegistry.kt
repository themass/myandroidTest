package com.openapi.ks.myapp.companion

import org.json.JSONArray
import org.json.JSONObject

/**
 * her 注册表摘要（服务端第一刀 `ca_her_pack`，`GET /v1/hers`）。
 * 呈现实体（renderer + 模型包），不是 persona——见 2026-11-28 persona/her 分层架构 §2。
 * 解析与 [CharacterSummary] 同款纪律：opt* 读取，缺省字段退化成空值，绝不抛。
 */
data class HerSummary(
    val id: String,
    val name: String,
    val tagline: String,
    /** 引擎判别长在这个实体上（不是角色上）：simple / live2d（未来值）。 */
    val renderer: String,
    /** 服务端子集状态：published 才会驱动端上选择。 */
    val status: String,
    val packVersion: Int = 0,
    val effectCount: Int = 0,
    val stickerCount: Int = 0,
    /** her 包默认音色的 voice_id（音色优先级：用户 her 覆盖 > her 包 > persona 卡）。 */
    val voiceId: String? = null,
    /**
     * 卡片缩略图的**短期签名 URL**（服务端第二刀 her 详情同源）。
     * 存储没配 / 服务端还没上这一刀 → 空串（列表页因此不画图，与「没素材不假装有图」同一口径）。
     * 签名 URL 是 bearer secret：不落盘、不进日志。
     */
    val thumbnailUrl: String = "",
    /** 上面那个 URL 的有效期（秒）；0 = 服务端没给，按「很快就过期」处理。 */
    val urlExpiresIn: Int = 0,
) {
    companion object {
        fun from(json: JSONObject) = HerSummary(
            id = json.optString("id"),
            name = json.optString("name"),
            tagline = json.optString("tagline"),
            renderer = json.optString("renderer"),
            status = json.optString("status"),
            packVersion = json.optInt("pack_version"),
            effectCount = json.optInt("effect_count"),
            stickerCount = json.optInt("sticker_count"),
            voiceId = json.optJSONObject("voice_ref")
                ?.optString("voice_id")?.trim()?.takeIf { it.isNotEmpty() },
            thumbnailUrl = json.optString("thumbnail_url"),
            urlExpiresIn = json.optInt("url_expires_in"),
        )
    }
}

data class HerPage(val items: List<HerSummary>, val total: Int)

// ---- her 详情（服务端第二刀：`GET /v1/hers/{id}`） ----

/**
 * sha256 十六进制：her 素材包与装饰素材唯一的完整性凭据。
 *
 * 哈希形状不合格 = 这个素材**不可下载**（校验无从做起），不是「先下下来再说」：
 * 端上没有第二条能证明字节没被中途换掉的路，所以宁可不装。
 */
fun isSha256Hex(value: String): Boolean = SHA256_HEX.matches(value.trim())

private val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")

/**
 * 模型包引用（`model_ref`）：这一个 her 的呈现素材本体。
 *
 * [downloadUrl] 是对象存储的**签名链接**（服务端可能给空串 = 存储没配好或链接没签出来）；
 * 取它时**不带**登录凭据——签名本身就是授权（与 [com.openapi.ks.myapp.avatar.AvatarSync] 同一口径）。
 */
data class HerModelRef(
    val asset: String,
    val version: Int,
    val hash: String,
    val downloadUrl: String,
    val urlExpiresIn: Int,
    val sizeBytes: Long,
) {
    /** 哈希形状合格才谈得上校验；不合格这个包就不能装。 */
    val hashUsable: Boolean get() = isSha256Hex(hash)

    /**
     * 现在能不能下载：版本号有效、哈希合格、链接在——三者缺一，UI 就说「暂不可下载」，
     * 不发起任何请求（一次注定装不上的下载只会浪费用户的流量和一份临时文件）。
     */
    val downloadable: Boolean
        get() = version > 0 && hashUsable && downloadUrl.trim().startsWith("http")

    companion object {
        /** 服务端没给 `model_ref`（老服务端 / 坏数据）：一个「什么都下不了」的空引用，不抛。 */
        val EMPTY = HerModelRef(asset = "", version = 0, hash = "", downloadUrl = "", urlExpiresIn = 0, sizeBytes = 0)

        fun from(json: JSONObject) = HerModelRef(
            asset = json.optString("asset"),
            version = json.optInt("version"),
            hash = json.optString("hash"),
            downloadUrl = json.optString("download_url"),
            urlExpiresIn = json.optInt("url_expires_in"),
            sizeBytes = json.optLong("size_bytes"),
        )
    }
}

/**
 * 装饰条目（`effects` 与 `stickers` 同形）：特效 / 贴纸的一个素材。
 *
 * 这一刀只把清单读出来、在详情里列名字与数量；逐条下载不在本刀范围（P0 只做 model_ref）。
 */
data class HerAssetItem(
    val id: String,
    val name: String,
    /** 挂载锚点：HEAD / BODY …（原样收下，端上不解释语义）。 */
    val anchor: String,
    val hash: String,
    val sizeBytes: Long,
    val url: String,
) {
    val hashUsable: Boolean get() = isSha256Hex(hash)

    companion object {
        fun from(json: JSONObject) = HerAssetItem(
            id = json.optString("id"),
            name = json.optString("name"),
            anchor = json.optString("anchor"),
            hash = json.optString("hash"),
            sizeBytes = json.optLong("size"),
            url = json.optString("url"),
        )
    }
}

/** her 包默认音色（`voice_ref`）；服务端给 null = 这个 her 不指定音色。 */
data class HerVoiceRef(
    val provider: String,
    val voiceId: String,
) {
    companion object {
        fun from(json: JSONObject) = HerVoiceRef(
            provider = json.optString("provider"),
            voiceId = json.optString("voice_id"),
        )
    }
}

/**
 * 人格卡片（`persona`）：**只读展示**。
 *
 * 换 her 不换人格（稿 02 的硬承诺）：这里显示的是这个 her 附带的说明文字，
 * 端上不据此改任何 persona 状态。
 */
data class HerPersona(
    val roleId: String,
    val name: String,
    val description: String,
    val personality: String,
) {
    companion object {
        fun from(json: JSONObject) = HerPersona(
            roleId = json.optString("role_id"),
            name = json.optString("name"),
            description = json.optString("description"),
            personality = json.optString("personality"),
        )
    }
}

/**
 * her 详情（`GET /v1/hers/{id}`）：形象（model_ref）+ 装饰（effects / stickers）+ 音色 + 人格卡片。
 *
 * 与 [HerSummary] 同一解析纪律：每个键 opt* 读取，缺失退化成空值 / 空列表，**绝不抛**——
 * 详情抽屉因此永远画得出来，画不出来的只是数据本身没有的那部分。
 * 哈希不合格（非 64 hex）由 [HerModelRef.hashUsable] 标成「不可下载」，不尝试安装。
 *
 * [urlExpiresIn] 覆盖两个图片链接（`thumbnail_url` / `portrait_url`）：服务端只在顶层给一个有效期。
 */
data class HerDetail(
    val id: String,
    val name: String,
    val tagline: String,
    val renderer: String,
    val status: String,
    val packVersion: Int,
    val source: String,
    val thumbnailUrl: String = "",
    val portraitUrl: String = "",
    val urlExpiresIn: Int = 0,
    val model: HerModelRef = HerModelRef.EMPTY,
    val effects: List<HerAssetItem> = emptyList(),
    val stickers: List<HerAssetItem> = emptyList(),
    val voiceRef: HerVoiceRef? = null,
    val persona: HerPersona? = null,
) {
    /**
     * 列表页那份摘要：详情到手后卡片上的计数以详情的清单为准
     * （服务端只在详情里给全 effects / stickers，列表里的计数可能缺）。
     */
    fun asSummary() = HerSummary(
        id = id,
        name = name,
        tagline = tagline,
        renderer = renderer,
        status = status,
        packVersion = if (packVersion > 0) packVersion else model.version,
        effectCount = effects.size,
        stickerCount = stickers.size,
        voiceId = voiceRef?.voiceId?.trim()?.takeIf { it.isNotEmpty() },
        thumbnailUrl = thumbnailUrl,
        urlExpiresIn = urlExpiresIn,
    )

    companion object {
        fun from(json: JSONObject) = HerDetail(
            id = json.optString("id"),
            name = json.optString("name"),
            tagline = json.optString("tagline"),
            renderer = json.optString("renderer"),
            status = json.optString("status"),
            packVersion = json.optInt("pack_version"),
            source = json.optString("source"),
            thumbnailUrl = json.optString("thumbnail_url"),
            portraitUrl = json.optString("portrait_url"),
            urlExpiresIn = json.optInt("url_expires_in"),
            model = json.optJSONObject("model_ref")?.let { HerModelRef.from(it) } ?: HerModelRef.EMPTY,
            effects = json.optJSONArray("effects").toItemList(),
            stickers = json.optJSONArray("stickers").toItemList(),
            voiceRef = json.optJSONObject("voice_ref")?.let { HerVoiceRef.from(it) },
            persona = json.optJSONObject("persona")?.let { HerPersona.from(it) },
        )
    }
}

/** 条目不是对象（坏数据）就跳过，而不是抛一个 JSONException 把整页详情弄没。 */
private fun JSONArray?.toItemList(): List<HerAssetItem> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let { HerAssetItem.from(it) } }
}
