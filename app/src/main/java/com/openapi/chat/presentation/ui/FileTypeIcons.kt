package com.openapi.chat.presentation.ui

import androidx.annotation.DrawableRes
import com.openapi.ks.chat.R

/**
 * 按文件扩展名选图标。移植自旧版 `FileTypeIconMapping.java` 的扩展名映射，复用现有的
 * `src_assets_files_*` 图片资源，不新增素材。
 *
 * 只走扩展名这一路：[com.openapi.chat.domain.model.FileAttachment] 不带 MIME 类型，
 * 旧版的 MIME 表在这里用不上。扩展名不认识的一律用「其他文件」图标，和旧版通配兜底一致。
 */
@DrawableRes
internal fun fileIconRes(fileName: String): Int =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "txt" -> R.drawable.src_assets_files_txt
        "pdf" -> R.drawable.src_assets_files_pdf
        "doc", "docx" -> R.drawable.src_assets_files_word
        "xls", "xlsx" -> R.drawable.src_assets_files_excel
        "ppt", "pptx" -> R.drawable.src_assets_files_ppt
        "html" -> R.drawable.src_assets_files_url
        "jpg", "jpeg", "png", "gif" -> R.drawable.src_assets_images_file
        else -> R.drawable.src_assets_files_other
    }
