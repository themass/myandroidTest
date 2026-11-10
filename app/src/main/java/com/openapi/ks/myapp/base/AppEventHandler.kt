package com.openapi.ks.myapp.base

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.openapi.core.event.UserNeedLogin
import com.openapi.core.util.EventBusUtil
import com.openapi.core.util.PreferenceUtils
import com.openapi.ks.myapp.constant.Constants
import com.openapi.ks.myapp.data.UserLoginUtil
import com.openapi.ks.myapp.data.config.PlayCoreEvent
import com.openapi.ks.myapp.ui.user.LoginActivity
import com.openapi.ks.myapp.video.PlaybackEngineHelper
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * 全局事件处理。
 *
 * 这些订阅原来写在旧抽屉页和旧首页里，只有那两个页面在前台时才生效；
 * 现在主页不再继承它们，改成在 Application 里注册一次，和页面生命周期无关。
 */
object AppEventHandler {

    fun register() {
        EventBusUtil.getEventBus().register(this)
    }

    /** 登录态失效（例如接口返回未登录）：清掉本地登录信息并回到登录页。 */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUserNeedLogin(event: UserNeedLogin) {
        val app = MyApplication.getInstance()
        UserLoginUtil.logout(app)
        app.startActivity(Intent(app, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 设置里切换播放内核。 */
    @OptIn(UnstableApi::class)
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayCore(event: PlayCoreEvent) {
        PlaybackEngineHelper.applyEngine(MyApplication.getInstance())
    }
}
