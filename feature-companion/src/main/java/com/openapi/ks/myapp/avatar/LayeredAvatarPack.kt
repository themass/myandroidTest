package com.openapi.ks.myapp.avatar

import org.json.JSONObject
import java.io.File

/**
 * 分层形象包：每层一张同尺寸的透明 PNG，由 manifest 的 `layers` 数组描述叠放顺序与分组。
 *
 * 与 [AvatarManifest]（立绘 + 表情的旧格式）并存：两者字段不同，解析器按格式各自校验，
 * 安装时任一通过即可入库。这里只描述文件，绘制与动作由 [LayeredAvatarEngine] 和 [LayeredAvatar] 负责。
 */
data class LayeredAvatarPack(
    val id: String,
    val width: Int,
    val height: Int,
    val pivotX: Float,
    val pivotY: Float,
    val layers: List<LayerFile>,
) {
    data class LayerFile(
        val id: String,
        val file: File,
        val z: Int,
        val group: String,
        /** 同一分组里的变体名，如眼睛 `open/half/closed`、嘴 `closed/A/I/U/E/O`；没有变体的层为 null。 */
        val variant: String?,
    )

    fun layer(id: String): LayerFile? = layers.firstOrNull { it.id == id }

    fun variant(group: String, variant: String): LayerFile? =
        layers.firstOrNull { it.group == group && it.variant == variant }

    /**
     * 每张图层是否都能被解码器识别（只读 PNG 头部，不解码像素）。
     * 为 false 时调用方应退回圆形头像，而不是画半个形象。
     */
    fun decodable(): Boolean = firstUndecodableLayer(this) == null

    companion object {
        class Invalid(message: String) : Exception(message)

        /** 没有这些层就无法合成一个完整的人：身体、脸、头发两层、双手、胸、双眼和嘴必须存在。 */
        private val REQUIRED_LAYERS = listOf("body", "chest", "face", "hair_back", "hair_front", "hand_l", "hand_r")
        private val REQUIRED_VARIANTS = mapOf(
            "eye_l" to "open",
            "eye_r" to "open",
            "mouth" to "closed",
        )

        /**
         * 解析并校验 manifest。引用的文件必须存在于 [root] 之内（防越界），缺少必需层直接失败，
         * 调用方应保留旧版本，不要用不完整的形象覆盖好的。
         */
        fun parse(json: String, root: File): LayeredAvatarPack {
            val manifest = try {
                JSONObject(json)
            } catch (e: Exception) {
                throw Invalid("manifest 不是合法的 JSON")
            }
            val canvas = manifest.optJSONObject("canvas") ?: throw Invalid("缺少 canvas")
            val width = canvas.optInt("w", 0)
            val height = canvas.optInt("h", 0)
            if (width <= 0 || height <= 0) throw Invalid("canvas 尺寸不合法")
            val pivot = manifest.optJSONObject("pivot")
            val array = manifest.optJSONArray("layers") ?: throw Invalid("缺少 layers")

            val rootPath = root.canonicalPath + File.separator
            val layers = (0 until array.length()).map { i ->
                val item = array.optJSONObject(i) ?: throw Invalid("layers[$i] 不是对象")
                val path = item.optString("path")
                if (path.isEmpty()) throw Invalid("layers[$i] 缺少 path")
                val file = File(root, path).canonicalFile
                if (!file.path.startsWith(rootPath) || !file.isFile) throw Invalid("引用的文件不存在或越界：$path")
                LayeredAvatarPack.LayerFile(
                    id = item.optString("id").ifEmpty { throw Invalid("layers[$i] 缺少 id") },
                    file = file,
                    z = item.optInt("z", 0),
                    group = item.optString("group"),
                    variant = item.optString("variant").takeIf { it.isNotEmpty() },
                )
            }.sortedBy { it.z }

            val byId = layers.associateBy { it.id }
            REQUIRED_LAYERS.firstOrNull { it !in byId }?.let { throw Invalid("缺少必需图层：$it") }
            REQUIRED_VARIANTS.forEach { (group, variant) ->
                if (layers.none { it.group == group && it.variant == variant }) {
                    throw Invalid("缺少必需图层：$group/$variant")
                }
            }

            return LayeredAvatarPack(
                id = manifest.optString("id"),
                width = width,
                height = height,
                pivotX = pivot?.optDouble("x", 0.5)?.toFloat() ?: 0.5f,
                pivotY = pivot?.optDouble("y", 0.78)?.toFloat() ?: 0.78f,
                layers = layers,
            )
        }

        /**
         * 只读 PNG 头部确认每张图层都能被解码器识别（[decodeScaled] 的 bounds 预读），
         * 不全量解码，所以便宜。返回第一个读不出来的路径，全部正常返回 null。
         *
         * manifest 校验只保证「文件在、字段全」，不保证那是有效图片；坏 PNG 到渲染阶段
         * 只会得到半张脸甚至空白。入库前和每次加载都过一遍，宁可退回圆形头像。
         */
        fun firstUndecodableLayer(pack: LayeredAvatarPack): String? =
            pack.layers.firstOrNull { decodeImageBounds(it.file) == null }?.file?.path
    }
}
