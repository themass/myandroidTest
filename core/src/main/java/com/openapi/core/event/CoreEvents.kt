package com.openapi.core.event

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * :core 里的应用事件。发布方调用 post*，订阅方用 [observe] 收集对应的 flow。
 * 新事件加在这里，不要再用 EventBus。
 */
object CoreEvents {

    private val _userNeedLogin = eventFlow<UserNeedLogin>()
    val userNeedLogin: SharedFlow<UserNeedLogin> = _userNeedLogin.readOnly()
    fun postUserNeedLogin() {
        _userNeedLogin.tryEmit(UserNeedLogin())
    }

    private val _favoriteChanged = eventFlow<FavoriteChangeEvent>()
    val favoriteChanged: SharedFlow<FavoriteChangeEvent> = _favoriteChanged.readOnly()
    fun postFavoriteChanged() {
        _favoriteChanged.tryEmit(FavoriteChangeEvent())
    }

    private val _configAction = eventFlow<ConfigActionEvent>()
    val configAction: SharedFlow<ConfigActionEvent> = _configAction.readOnly()
    fun postConfigAction(event: ConfigActionEvent) {
        _configAction.tryEmit(event)
    }
}
