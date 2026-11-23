package com.openapi.ks.myapp.avatar

import org.json.JSONObject
import java.io.File

/** 形象包的 manifest.json（服务端 domain/avatar/package.py 定义了同一份格式）。 */
object AvatarManifest {
    const val SCHEMA_VERSION = 1

    class Invalid(message: String) : Exception(message)

    /**
     * 把 manifest 解析成本地文件。引用的文件必须存在于 [root] 之内；解析失败抛 [Invalid]，
     * 调用方应当保留旧版本，不要用半个形象覆盖好的。
     */
    fun parse(json: String, root: File): AvatarFiles {
        val manifest = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw Invalid("manifest 不是合法的 JSON")
        }
        if (manifest.optInt("schema_version", -1) != SCHEMA_VERSION) throw Invalid("不支持的 schema_version")
        val layers = manifest.optJSONObject("layers") ?: throw Invalid("缺少 layers")
        val portrait = layers.optString("portrait").takeIf { it.isNotEmpty() } ?: throw Invalid("缺少立绘")

        fun file(path: String?): File? {
            if (path.isNullOrEmpty()) return null
            val f = File(root, path).canonicalFile
            if (!f.path.startsWith(root.canonicalPath + File.separator) || !f.isFile) {
                throw Invalid("引用的文件不存在或越界：$path")
            }
            return f
        }

        fun group(name: String): Map<String, File> {
            val obj = manifest.optJSONObject(name) ?: return emptyMap()
            return obj.keys().asSequence().mapNotNull { key -> file(obj.optString(key))?.let { key to it } }.toMap()
        }

        fun layer(key: String): File? = file(layers.optString(key).takeIf { it.isNotEmpty() })

        return AvatarFiles(
            portrait = file(portrait)!!,
            expressions = group("expressions"),
            brows = group("brows"),
            mouthClosed = layer("mouth_closed"), mouthOpen = layer("mouth_open"), mouthHalf = layer("mouth_half"),
            eyeOpen = layer("eye_open"), eyeClosed = layer("eye_closed"),
            pupilLeft = layer("pupil_left"), pupilRight = layer("pupil_right"),
            chest = layer("chest"), lowerBody = layer("lower_body"),
            blush = layer("blush"), highlight = layer("highlight"),
            motion = manifest.optJSONObject("motion") ?: JSONObject(),
        )
    }
}
