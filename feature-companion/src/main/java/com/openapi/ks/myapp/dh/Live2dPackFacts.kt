package com.openapi.ks.myapp.dh

import com.openapi.ks.dh.EngineAssets
import org.json.JSONObject
import java.io.File

/**
 * 模型包的「事实」读入（触摸刀 C6）：这个形象**声明**了哪些触摸区、
 * 这个模型**注册**了哪些表情与动作。两者合起来决定一次点击能落到什么反应。
 *
 * 为什么必须从包里读，而不是在代码里按目录名分支：旧引擎写的是
 * `fullWardrobe = activeModelDir == "ATRI"`——「ATRI 四轴全开、其余只有脸轴」这件事
 * 其实是**资产盘点的事实**（ATRI 注册 180 个组合表情，Amadeus/Yuuka 只有 base+blush），
 * 一旦写死，加一个模型或补一套资产都得改代码。这里把它还给包自己说。
 *
 * 设备上的落点（scripts/dh-lab/seed-assets.sh 的 live2d 段）：模型目录
 * `<files>/dh-engines/live2d/<model>/`，lab 覆盖层整目录推进去，所以
 * `wardrobe-spec.json` 与 `<model>.model3.json` 都在同一目录里。
 *
 * 三态回落与决策层一致：
 * - 没有 `wardrobe-spec.json`（Amadeus/Yuuka 的 lab 覆盖层就没有）→ [Facts.declaration] 为 null
 *   → 用 [TouchZoneRouter.builtIn] 兜底分区（不发明命中区）；
 * - 读不到 model3 → [Facts.registered] 为 null → **不设表情闸**（退成刀之前的行为，
 *   而且这种目录本来就过不了 [com.openapi.ks.dh.Live2dModelGate]，不会出画面）；
 * - 声明坏掉 → [Facts.errors] 非空且 declaration 为 null，日志打出来（不静默）。
 */
object Live2dPackFacts {

    data class Facts(
        val declaration: TouchReactionDeclaration?,
        val registered: Set<String>?,
        val modelOwnCues: List<TouchCue>,
        val errors: List<String>,
    ) {
        /** 表情闸：注册表读不到时放行（见类注释的三态回落）。 */
        fun isRegistered(expression: String): Boolean = registered?.contains(expression) ?: true
    }

    fun load(modelDir: File, modelJsonName: String): Facts {
        val specFile = File(modelDir, "wardrobe-spec.json")
        var declaration: TouchReactionDeclaration? = null
        val errors = mutableListOf<String>()
        if (specFile.isFile) {
            val parsed = WardrobeTouchReactions.parse(runCatching { specFile.readText() }.getOrElse { "" })
            declaration = parsed.declaration
            errors += parsed.errors.map { "wardrobe-spec: $it" }
        }
        val model3 = File(modelDir, modelJsonName)
        var registered: Set<String>? = null
        var own: List<TouchCue> = emptyList()
        if (model3.isFile) {
            val obj = runCatching { JSONObject(model3.readText()) }.getOrNull()
            if (obj == null) {
                errors += "$modelJsonName 不是合法 JSON（表情闸退成放行）"
            } else {
                registered = expressionNames(obj)
                own = modelOwnCues(obj)
            }
        }
        return Facts(declaration, registered, own, errors)
    }

    /** 设备目录布局的薄封装（与 Live2dModelChoice / Live2dRuntime.AndroidEnv 同一 root 判据）。 */
    fun modelDir(assetRoot: File, modelDirName: String): File =
        File(File(assetRoot, EngineAssets.LIVE2D.name.lowercase()), modelDirName)

    /** model3 里注册的表情名：`FileReferences.{Expressions|FileExpressions}`。 */
    private fun expressionNames(model3: JSONObject): Set<String> {
        val refs = model3.optJSONObject("FileReferences") ?: return emptySet()
        val out = mutableSetOf<String>()
        for (key in listOf("Expressions", "FileExpressions")) {
            val arr = refs.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val name = item.optString("Name")
                if (name.isNotEmpty()) out += name
            }
        }
        return out
    }

    /**
     * 模型自带的动作声明（回落链第二级 MODEL_OWN）：`FileReferences.FileMotions[]` 的动作组。
     *
     * 今天它们不参与抽签——fork 的 JNI 只有 `nativeApplyExpression`（无 motion API），
     * 能力门（[TouchReactionDecision] 的 `supported`）会把 MOTION 滤掉。这一级现在就接好，
     * 是为了让「模型自带 tapMotions」这条回落在 fork 补上 motion API 那天不需要再改结构；
     * her pack 的第二刀（payload 的 `touch_zones[]`）落地后也从这里接入。
     */
    private fun modelOwnCues(model3: JSONObject): List<TouchCue> {
        val refs = model3.optJSONObject("FileReferences") ?: return emptyList()
        val arr = refs.optJSONArray("FileMotions") ?: return emptyList()
        val out = mutableListOf<TouchCue>()
        for (i in 0 until arr.length()) {
            val group = arr.optJSONObject(i) ?: continue
            val groupName = group.optString("Group")
            if (groupName.isEmpty()) continue
            val motions = group.optJSONArray("Motions")
            if (motions != null && motions.length() > 0) {
                for (j in 0 until motions.length()) {
                    val id = motions.optJSONObject(j)?.optString("Id") ?: continue
                    if (id.isNotEmpty()) out += TouchCue(TouchCueKind.MOTION, motionId = id)
                }
            } else {
                out += TouchCue(TouchCueKind.MOTION, motionId = groupName)
            }
        }
        return out
    }

    /** 空 Facts：连 Context 都没有（JVM 单测里没建 View 的引擎）= 完全退成刀之前的行为。 */
    val EMPTY = Facts(null, null, emptyList(), emptyList())
}
