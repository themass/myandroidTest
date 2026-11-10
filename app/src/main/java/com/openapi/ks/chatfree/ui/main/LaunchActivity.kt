package com.openapi.ks.chatfree.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.View
import com.openapi.core.ads.yewu.ads.base.AdsContext
import com.openapi.core.ads.yewu.ads.base.AdsManager
import com.openapi.core.ads.yewu.ads.config.LaunchAdsNext
import com.openapi.core.ads.yewu.ads.config.SplashAdDissmisEvent
import com.openapi.core.ads.yewu.um.MobAgent
import com.openapi.core.util.EventBusUtil
import com.openapi.core.util.LogUtil
import com.openapi.home.HomeActivity
import com.openapi.ks.chat.R
import com.openapi.ks.chat.databinding.MainLaunchBinding
import com.openapi.ks.myapp.constant.Constants
import com.openapi.ks.myapp.task.LoginTask
import com.openapi.ks.myapp.ui.base.app.LogActivity
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

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
        EventBusUtil.getEventBus().register(this)
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
        EventBusUtil.getEventBus().unregister(this)
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBannerShow(next: LaunchAdsNext) {
        LogUtil.i("LaunchAdsNext " + next.from)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onADDismissed(event: SplashAdDissmisEvent) {
        AdsManager.getInstans().showSplashAds(
            AdsContext.AdsFrom.ADVIEW, this, launchBinding.rlSpread, launchBinding.skipView,
        )
    }
}
