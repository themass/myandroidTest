package com.openapi.ks.myapp.avatar

import org.json.JSONObject
import java.io.File

/**
 * 下载的形象包里的素材：每层一个已经解压到本地的图片文件。和 [AvatarSpec] 的资源 id 并列——
 * 内置形象用资源 id，下载的形象用文件；两者都没有的层就不画。
 *
 * 路径都在形象包安装目录之内（解压时已校验），渲染层不再做路径判断。
 */
data class AvatarFiles(
    val portrait: File,
    val expressions: Map<String, File> = emptyMap(),
    val brows: Map<String, File> = emptyMap(),
    val mouthClosed: File? = null,
    val mouthOpen: File? = null,
    val mouthHalf: File? = null,
    val eyeOpen: File? = null,
    val eyeClosed: File? = null,
    val pupilLeft: File? = null,
    val pupilRight: File? = null,
    val chest: File? = null,
    val lowerBody: File? = null,
    val blush: File? = null,
    val highlight: File? = null,
    /** 动作参数（视线范围、微动作间隔等），服务端原样透传，渲染器按需读取。 */
    val motion: JSONObject = JSONObject(),
)
