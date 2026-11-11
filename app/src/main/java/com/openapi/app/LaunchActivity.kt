package com.openapi.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.View
import com.openapi.common.ads.base.AdsContext
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
        MobAgent.init(this)
        LoginTask.start(this)
        countdownHandler.postDelayed(startMainRunnable, max.toLong())
        observeAdEvents()
        showAdview()
    }

    private fun skip(view: View) {
        if (resources.getText(R.string.skip) == launchBinding.tvShu.text) launch()
    }

    private fun launch() {
        startActivity(Intent(this, HomeActivity::class.java))
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
