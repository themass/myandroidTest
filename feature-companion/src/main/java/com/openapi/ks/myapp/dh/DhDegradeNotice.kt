package com.openapi.ks.myapp.dh

/** 请求了 native 引擎却落在 SIMPLE 时给用户的一句解释；没降级返回 null。 */
internal object DhDegradeNotice {
    fun text(requested: DhEngineId, actual: DhEngineId, quarantined: Boolean): String? {
        if (actual != DhEngineId.SIMPLE || requested == DhEngineId.SIMPLE) return null
        return if (quarantined) {
            "该形象上次运行异常，已临时停用。可在菜单里重新选择再试"
        } else {
            "该形象在此设备上不可用，已切换为基础形象"
        }
    }
}
