package com.openapi.ks.myapp.dh

import android.content.Context

/**
 * 通话舞台的用户意愿，三态——这是「服务端能不能决定引擎」的分界：
 * 只有 [Unset]（用户从没在引擎菜单里表达过偏好）才让服务端 her 注册表决定引擎。
 * [Original] 与 [Engine] 都是显式选择，任何服务端值都不覆盖（用户的手比接口大）。
 */
sealed interface StageChoice {
    /** 没存过偏好：跟随 her 注册表（没有 her 就是原形象）。 */
    data object Unset : StageChoice

    /** 显式选了「原来的形象」（分层立绘 / 圆头像）。 */
    data object Original : StageChoice

    /** 显式选了某个数字人引擎。 */
    data class Engine(val id: DhEngineId) : StageChoice
}

/** Lab 偏好：上次引擎 + 是否强制打开 Lab（非 debuggable 也可开）。 */
class DhLabPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var engine: DhEngineId
        get() = prefs.getString(KEY_ENGINE, null)
            ?.let { runCatching { DhEngineId.valueOf(it) }.getOrNull() }
            ?: DhEngineId.SIMPLE
        set(value) {
            prefs.edit().putString(KEY_ENGINE, value.name).apply()
        }

    /**
     * 通话页右上角下拉的舞台选择——三态（[StageChoice]）：
     * - `Unset`（没存过）= 用户没表达过偏好，服务端 her 注册表可以决定引擎（第二刀）；
     * - `Original`（存 "ORIGINAL"）= 用户显式选了「原来的形象」，谁也不许盖掉它；
     * - `Engine(id)` = 用户显式选了某个引擎，同样不被服务端覆盖。
     *
     * 存储与旧版完全兼容：旧版没选过=键缺失、显式原形象=键值 "ORIGINAL"，这个区分
     * 本来就在盘上，只是旧 getter 把两者抹成了同一个 null。与 [engine] 分开：后者默认
     * SIMPLE，不能拿来决定要不要盖掉原来的舞台，否则 Lab 一开形象就没了。
     */
    var callStageChoice: StageChoice
        get() {
            val raw = prefs.getString(KEY_CALL_STAGE, null) ?: return StageChoice.Unset
            if (raw.isBlank() || raw == ORIGINAL) return StageChoice.Original
            return runCatching { StageChoice.Engine(DhEngineId.valueOf(raw)) }
                .getOrDefault(StageChoice.Original)
        }
        set(value) {
            val raw = when (value) {
                StageChoice.Unset -> null
                StageChoice.Original -> ORIGINAL
                is StageChoice.Engine -> value.id.name
            }
            prefs.edit().putString(KEY_CALL_STAGE, raw).apply()
        }

    var labForced: Boolean
        get() = prefs.getBoolean(KEY_LAB_FORCED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_LAB_FORCED, value).apply()
        }

    /**
     * Live2D 当前形象（多形象切换，用户 2026-11-23）。存原始字符串、读时过
     * [Live2dModels.sanitize] 白名单——脏值回落 ATRI，永远回一个合法目录名。
     */
    var live2dModel: String
        get() = Live2dModels.sanitize(prefs.getString(KEY_LIVE2D_MODEL, null))
        set(value) {
            prefs.edit().putString(KEY_LIVE2D_MODEL, Live2dModels.sanitize(value)).apply()
        }

    companion object {
        const val PREFS = "companion_dh_lab"
        private const val KEY_ENGINE = "companion.dh_engine"
        private const val KEY_CALL_STAGE = "companion.dh_call_stage"
        private const val KEY_LAB_FORCED = "companion.dh_lab"
        private const val KEY_LIVE2D_MODEL = "companion.dh_live2d_model"
        private const val ORIGINAL = "ORIGINAL"
    }
}
