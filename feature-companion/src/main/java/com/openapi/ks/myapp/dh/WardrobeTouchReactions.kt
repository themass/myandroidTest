package com.openapi.ks.myapp.dh

import org.json.JSONArray
import org.json.JSONObject

/**
 * wardrobe-spec 的 `touchReactions` 块：解析 + 校验（单一事实源的读入侧）。
 *
 * 为什么校验必须在这一侧做，waifu 深读 §10.2 纪律 2 说得最直接：
 * 「payload 里每加一个字段，就必须同时有一个生产者、一个校验点和一个测试，否则它一定变成
 * 死代码」。生产者是 `scripts/dh-lab/live2d-lab-assets/wardrobe-spec.json`；校验点就是这个
 * 文件；测试是 `WardrobeTouchReactionsTest`（读真实出货文件）+ 路由/决策两个消费侧测试。
 *
 * 校验失败**不静默**：[ParseResult.errors] 逐条指出第几区第几条 cue 坏在哪。声明坏掉时
 * [ParseResult.declaration] 为 null，引擎退回兜底分区（`TouchZoneRouter.builtIn`）——
 * 也就是「退回现状」，而不是「按坏数据乱播」。
 *
 * 词表是封闭的：`kind ∈ {cycle, variant, motion}`、rect 是四个归一化数的数组。
 * 未知 kind / 未知槽位 / 引用 pending 变体 / 非正权重 / 越界或反向矩形 / 重复区 id 全部拦。
 * 对**未知字段**则是容忍（只增不改的版本策略，VTS `README.md:171`）——旧引擎读新 spec 不该崩。
 */
object WardrobeTouchReactions {

    /** 解析结果：坏声明给 null + 错误列表；没有这个块给 null + 空列表（合法的「未声明」）。 */
    data class ParseResult(val declaration: TouchReactionDeclaration?, val errors: List<String>)

    /** spec 里一个槽位的读入视图（校验 cue 引用是否合法用）。 */
    data class SlotInfo(
        val slot: String,
        val label: String,
        val region: String,
        val tokens: Map<String, TokenInfo>,
    )

    data class TokenInfo(val token: String, val label: String, val status: String)

    /** 槽位词表（含 pending 槽；[SlotInfo.tokens] 的 status 供校验判「能不能出货」）。 */
    fun parseSlots(text: String): Map<String, SlotInfo> {
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
        val arr = obj.optJSONArray("slots") ?: return emptyMap()
        val out = LinkedHashMap<String, SlotInfo>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val slot = s.optString("slot")
            if (slot.isEmpty()) continue
            val tokens = LinkedHashMap<String, TokenInfo>()
            val variants = s.optJSONArray("variants") ?: JSONArray()
            for (j in 0 until variants.length()) {
                val v = variants.optJSONObject(j) ?: continue
                val token = v.optString("token")
                if (token.isEmpty()) continue
                tokens[token] = TokenInfo(token, v.optString("label"), v.optString("status"))
            }
            out[slot] = SlotInfo(slot, s.optString("label"), s.optString("region"), tokens)
        }
        return out
    }

    fun parse(text: String): ParseResult {
        val obj = runCatching { JSONObject(text) }.getOrNull()
            ?: return ParseResult(null, listOf("wardrobe-spec 不是合法 JSON"))
        val block = obj.optJSONObject("touchReactions") ?: return ParseResult(null, emptyList())
        val slots = parseSlots(text)
        val errors = mutableListOf<String>()
        val zones = mutableListOf<TouchReactionZone>()
        val seenIds = mutableSetOf<String>()
        val zoneArr = block.optJSONArray("zones")
        if (zoneArr == null || zoneArr.length() == 0) {
            return ParseResult(null, listOf("touchReactions.zones 缺失或为空"))
        }
        for (i in 0 until zoneArr.length()) {
            val z = zoneArr.optJSONObject(i)
            if (z == null) {
                errors += "zones[$i]: 不是对象"
                continue
            }
            val id = z.optString("id")
            when {
                id.isEmpty() -> errors += "zones[$i]: 缺 id（区必须自带稳定 id）"
                !ZONE_ID.matches(id) -> errors += "zones[$i]: id \"$id\" 不合法（^[a-z][a-z0-9_.-]{0,63}$）"
                !seenIds.add(id) -> errors += "zones[$i]: 重复 id \"$id\"（语义 id 是仲裁的 key，必须唯一）"
            }
            val rects = rectsOf(z.optJSONArray("rects"))
            if (rects == null) {
                errors += "zones[$i]($id): rects 必须是若干条 [x0,y0,x1,y1]，且 0<=min<max<=1"
            }
            val reactions = mutableListOf<TouchCue>()
            val cueArr = z.optJSONArray("reactions")
            if (cueArr == null || cueArr.length() == 0) {
                errors += "zones[$i]($id): reactions 不能为空（声明了区却没有反应 = 死字段）"
            } else {
                for (k in 0 until cueArr.length()) {
                    val c = cueArr.optJSONObject(k)
                    if (c == null) {
                        errors += "zones[$i]($id).reactions[$k]: 不是对象"
                        continue
                    }
                    val cue = cueOf(c, slots, i, id, k, errors) ?: continue
                    reactions += cue
                }
            }
            zones += TouchReactionZone(
                id = id,
                label = z.optString("label").ifEmpty { id },
                // priority 缺省时按声明序（可预期），显式值优先——但显式值必须存在才能重叠可判。
                priority = if (z.has("priority")) z.optInt("priority", i) else i,
                rects = rects ?: emptyList(),
                reactions = reactions,
            )
        }
        return if (errors.isEmpty()) {
            ParseResult(TouchReactionDeclaration(zones), emptyList())
        } else {
            ParseResult(null, errors)
        }
    }

    private val ZONE_ID = Regex("^[a-z][a-z0-9_.-]{0,63}$")

    /** 归一化矩形数组；任何一条不合法就整体判失败（返回 null），不做「丢掉坏的那条」的半吊子。 */
    private fun rectsOf(arr: JSONArray?): List<TouchRect>? {
        if (arr == null || arr.length() == 0) return null
        val out = mutableListOf<TouchRect>()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONArray(i) ?: return null
            if (r.length() != 4) return null
            val vals = (0 until 4).map { r.optDouble(it, Double.NaN) }
            if (vals.any { it.isNaN() || it < 0.0 || it > 1.0 }) return null
            val (x0, y0, x1, y1) = vals
            if (x1 <= x0 || y1 <= y0) return null
            out += TouchRect(x0, y0, x1, y1)
        }
        return out
    }

    private fun cueOf(
        c: JSONObject,
        slots: Map<String, SlotInfo>,
        zoneIndex: Int,
        zoneId: String,
        cueIndex: Int,
        errors: MutableList<String>,
    ): TouchCue? {
        val where = "zones[$zoneIndex]($zoneId).reactions[$cueIndex]"
        val kindName = c.optString("kind")
        val kind = when (kindName) {
            "cycle" -> TouchCueKind.CYCLE
            "variant" -> TouchCueKind.VARIANT
            "motion" -> TouchCueKind.MOTION
            // 未知 kind 明确报错而不是忽略：忽略会让写错的声明静默变成「这一区没反应」。
            else -> {
                errors += "$where: 未知 kind \"$kindName\"（cycle|variant|motion）"
                return null
            }
        }
        val weight = if (c.has("weight")) c.optInt("weight", 0) else 1
        if (weight <= 0) {
            errors += "$where: weight 必须是正整数（收到 $weight）"
            return null
        }
        return when (kind) {
            TouchCueKind.MOTION -> {
                val motionId = c.optString("motionId").ifEmpty { c.optString("group") }
                if (motionId.isEmpty()) {
                    errors += "$where: motion 需要 motionId"
                    null
                } else {
                    TouchCue(kind = kind, motionId = motionId, weight = weight)
                }
            }
            else -> {
                val slot = c.optString("slot")
                val info = slots[slot]
                if (info == null) {
                    errors += "$where: 未知 slot \"$slot\"（反应只能引用 slots 里声明过的轴）"
                    return null
                }
                if (kind == TouchCueKind.CYCLE) {
                    TouchCue(kind = kind, slot = slot, weight = weight)
                } else {
                    val token = c.optString("token")
                    val variant = info.tokens[token]
                    if (variant == null) {
                        errors += "$where: slot \"$slot\" 没有 token \"$token\"（不发明资产）"
                    } else if (variant.status != "active") {
                        errors += "$where: \"$slot:$token\" 是 ${variant.status}，素材未出货不能被触摸触到"
                    } else {
                        return TouchCue(kind = kind, slot = slot, token = token, weight = weight)
                    }
                    null
                }
            }
        }
    }
}
