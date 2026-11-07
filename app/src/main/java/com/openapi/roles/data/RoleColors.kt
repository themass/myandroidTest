package com.openapi.roles.data

/**
 * 目录里的颜色字段来自原始角色文件，既有 "blue" 这样的名字，也有 "#2563EB" 这样的十六进制，
 * 偶尔还有 "neon-cyan" 之类的自造词。这里统一成 ARGB，认不出的回落到中性灰蓝，
 * 保证列表里不会因为某个角色颜色写得奇怪就崩或者透明。
 */
object RoleColors {
    const val FALLBACK: Long = 0xFF64748B

    private val named = mapOf(
        "blue" to 0xFF3B82F6,
        "green" to 0xFF22C55E,
        "orange" to 0xFFF97316,
        "purple" to 0xFF8B5CF6,
        "teal" to 0xFF14B8A6,
        "indigo" to 0xFF6366F1,
        "red" to 0xFFEF4444,
        "amber" to 0xFFF59E0B,
        "cyan" to 0xFF06B6D4,
        "pink" to 0xFFEC4899,
        "violet" to 0xFF7C3AED,
        "gold" to 0xFFD4A017,
        "slate" to 0xFF64748B,
        "yellow" to 0xFFEAB308,
        "navy" to 0xFF1E3A8A,
        "fuchsia" to 0xFFD946EF,
        "lime" to 0xFF84CC16,
        "rose" to 0xFFF43F5E,
        "gray" to 0xFF6B7280,
        "grey" to 0xFF6B7280,
        "metallic-blue" to 0xFF3B6EA5,
        "neon-cyan" to 0xFF22D3EE,
        "neon-green" to 0xFF4ADE80,
    )

    fun parse(value: String?): Long {
        val v = value?.trim()?.lowercase().orEmpty()
        if (v.isEmpty()) return FALLBACK
        named[v]?.let { return it }
        if (v.startsWith("#")) {
            val hex = v.substring(1)
            val rgb = when (hex.length) {
                6 -> hex.toLongOrNull(16)
                3 -> hex.map { "$it$it" }.joinToString("").toLongOrNull(16)
                else -> null
            }
            if (rgb != null) return 0xFF000000 or rgb
        }
        return FALLBACK
    }
}
