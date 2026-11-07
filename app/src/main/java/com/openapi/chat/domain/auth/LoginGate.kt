package com.openapi.chat.domain.auth

import android.content.Context

/**
 * 需要登录的操作（发图片、发文件）在执行前调用的检查点。
 *
 * 聊天模块不直接依赖账号体系和登录页，由 app 注入实现（见 ChatDependencies.installLoginGate）。
 */
interface LoginGate {

    /** 已登录返回 true；未登录时由实现负责提示并引导到登录页，然后返回 false。 */
    fun ensureLoggedIn(context: Context): Boolean
}
