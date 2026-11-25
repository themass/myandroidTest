package com.openapi.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.View
import com.openapi.common.ads.base.AdsContext
import com.openapi.common.ads.base.AdsGate
import com.openapi.common.ads.base.AdsManager
import com.openapi.common.ads.config.AdsEvents
import com.openapi.common.ads.analytics.MobAgent
import com.openapi.common.event.observe
import androidx.lifecycle.lifecycleScope
import com.openapi.common.util.log.LogUtil
import com.openapi.app.home.HomeActivity
import com.openapi.app.R
import com.openapi.app.databinding.MainLaunchBinding
import com.openapi.core.config.Constants
import com.openapi.feature.user.data.task.LoginTask
import com.openapi.core.legacyui.app.LogActivity

/** 启动页：展示开屏广告，倒计时结束或点跳过后进入主页。 */
class LaunchActivity : LogActivity() {

    companion object {
        /** debug 包验证广告链路的开关 extra（见 [optInAdsForTesting]）。 */
        const val EXTRA_ADS_ON = "ads_on"
    }

    private lateinit var launchBinding: MainLaunchBinding
    private val max = Constants.STARTUP_SHOW_TIME_6000 + 1000
    private var now = 0

    private val startMainRunnable = Runnable { launch() }

    private val countdownHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            now += 1000
            if (now < max) {
                launchBinding.tvShu.text = "${(max - now) / 1000} s"
                delay1s()
            } else {
                launchBinding.tvShu.setText(R.string.skip)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchBinding = MainLaunchBinding.inflate(layoutInflater)
        setContentView(launchBinding.root)
        launchBinding.skipView.setOnClickListener { skip(it) }
        optInAdsForTesting()
        MobAgent.init(this)
        LoginTask.start(this)
        // 剧本房 adb 验收带 script_case：跳过开屏倒计时/广告，立刻进主页，避免 hello 门禁超时。
        if (!intent.getStringExtra(com.openapi.app.home.HomeActivity.EXTRA_SCRIPT_CASE).isNullOrBlank()) {
            launch()
            return
        }
        countdownHandler.postDelayed(startMainRunnable, max.toLong())
        observeAdEvents()
        showAdview()
    }

    /**
     * debug 包默认不加载广告（`28b2226e` 保护真机验收不被全屏广告干扰），
     * 但那样验收就永远看不到广告链路。台账 §6 D1 的口子：启动 Intent 带
     * `--ez ads_on 1` 时只在这一个进程内打开闸，并补一次 SDK 初始化
     * （MyApplication.onCreate 跑的时候闸还关着）。release 不受影响。
     *
     * `adb shell am start -n <pkg>/com.openapi.app.LaunchActivity --ez ads_on 1`
     */
    private fun optInAdsForTesting() {
        if (!intent.getBooleanExtra(EXTRA_ADS_ON, false)) return
        AdsGate.debugOptIn = true
        AdsManager.getInstans().initIfNeeded(applicationContext)
        LogUtil.i("ads opt-in via launch intent")
    }

    private fun skip(view: View) {
        if (resources.getText(R.string.skip) == launchBinding.tvShu.text) launch()
    }

    private fun launch() {
        // 把启动 Intent 上的 extras 转给主页：adb / deep link 带的 script_case 等
        // 不能丢在开屏页。HomeActivity 是 singleTask，冷启动走这里，热启动走它自己的 onNewIntent。
        startActivity(Intent(this, HomeActivity::class.java).putExtras(intent))
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        MobAgent.onResume(this)
    }

    override fun onPause() {
        super.onPause()
        MobAgent.onPause(this)
    }

    override fun onDestroy() {
        countdownHandler.removeMessages(Constants.ADS_JISHI)
        countdownHandler.removeCallbacks(startMainRunnable)
        AdsManager.getInstans().exitSplashAds(this, launchBinding.rlSpread)
        super.onDestroy()
    }

    private fun showAdview() {
        AdsManager.getInstans().showSplashAds(
            AdsContext.AdsFrom.ADMOB, this, launchBinding.rlSpread, launchBinding.skipView,
        )
        delay1s()
    }

    private fun delay1s() {
        countdownHandler.sendEmptyMessageDelayed(Constants.ADS_JISHI, 1000)
    }

    /** 随页面生命周期收集广告事件：页面销毁时自动停止，不用手动 unregister。 */
    private fun observeAdEvents() {
        observe(lifecycleScope, AdsEvents.launchAdsNext()) { next ->
            LogUtil.i("LaunchAdsNext " + next.from)
        }
        observe(lifecycleScope, AdsEvents.splashDismissed()) {
            AdsManager.getInstans().showSplashAds(
                AdsContext.AdsFrom.ADVIEW, this, launchBinding.rlSpread, launchBinding.skipView,
            )
        }
    }
}
