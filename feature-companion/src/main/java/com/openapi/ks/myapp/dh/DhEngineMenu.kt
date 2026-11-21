package com.openapi.ks.myapp.dh

/** 通话页右上角下拉里的一行。id 为 null 表示原来的形象，不换舞台。 */
data class DhEngineMenuRow(
    val id: DhEngineId?,
    val label: String,
    /** false：点了不换舞台，菜单上写出 [detail]。素材未下载但下完能用的引擎仍为 true（点了先下载）。 */
    val switches: Boolean,
    val detail: String? = null,
)

fun DhEngineMenuRow.menuLabel(selected: Boolean): String {
    val mark = if (selected) "✓ " else "　 "
    val extra = detail?.let { "（$it）" }.orEmpty()
    return mark + label + extra
}

/** 第一项永远是原来的形象；后面按注册表顺序列出各引擎。 */
fun dhEngineMenuRows(availability: List<DhEngineAvailability>): List<DhEngineMenuRow> {
    val engines = availability.map { row ->
        DhEngineMenuRow(
            id = row.id,
            label = dhEngineLabel(row.id),
            switches = row.available || row.needsDownload,
            detail = if (row.available) null else row.reason,
        )
    }
    return listOf(DhEngineMenuRow(id = null, label = "原来的形象", switches = true)) + engines
}

fun dhEngineLabel(id: DhEngineId): String = when (id) {
    DhEngineId.SIMPLE -> "Simple"
    DhEngineId.LIVE2D -> "Live2D"
    DhEngineId.DUIX -> "Duix"
    DhEngineId.TAO_AVATAR -> "Tao"
    DhEngineId.VRM -> "VRM (实验)"
}
