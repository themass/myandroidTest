package com.openapi.app.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.openapi.common.ads.base.AdsContext
import com.openapi.common.ads.base.AdsManager
import com.openapi.common.ads.base.InterstitialCadence
import com.openapi.speech.audio.AudioPlayer
import com.openapi.app.home.presentation.HomeScreen
import com.openapi.common.ui.theme.AppMaterialTheme

/**
 * 主页：底部三个标签（对话、频道、我的）。启动页直接进入这里。
 *
 * 对话标签用的就是原来单独的对话页所用的 ChatRoute，频道和「我的」承接了旧抽屉和旧首页的入口。
 *
 * 剧本房验收 extras（经 LaunchActivity 转发，或 adb 直打本 Activity）：
 *   --es script_case multi_seg
 *   --es character_id linche   # 可选，默认 linche
 */
class HomeActivity : ComponentActivity() {

    private var interstitialTried = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppMaterialTheme { HomeScreen() }
        }
        if (savedInstanceState == null) {
            HomeDependencies.require().onHomeStarted(this)
            showHomeInterstitialOnce()
        }
    }

    /**
     * AD2 恢复（台账 §3）：旧首页 `MainFragmentViewPage.java:133` 进主页即弹插屏，
     * 迁 Compose 时没搬（死亡点 `eefb98a9`）。这里恢复为**进程内最多一次**，且必须
     * 过 `InterstitialCadence` 的时机闸门（启动静默期已过、距上次插屏足够远）与
     * `AdsContext.rateShow()` 的窗口概率——旧「每次进主页必弹」正是 Play 拒审的形状。
     * 剧本房验收（`script_case`）不弹：广告全屏页会打断 adb 剧本。
     */
    private fun showHomeInterstitialOnce() {
        if (!intent.getStringExtra(EXTRA_SCRIPT_CASE).isNullOrBlank()) return
        // 稍等一拍：开屏广告（AppOpenAd 全屏页）刚关闭的瞬间不接力插屏。
        window.decorView.postDelayed({
            if (interstitialTried || isFinishing) return@postDelayed
            interstitialTried = true
            if (!InterstitialCadence.allow() || !AdsContext.rateShow()) return@postDelayed
            InterstitialCadence.noteShown()
            AdsManager.getInstans()
                .showInterstitialAds(this, AdsContext.Categrey.CATEGREY_VPN, false, AdsContext.AdsFrom.ADMOB, 1)
        }, 1_500)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask：热启动只换 Intent，不重建 Activity；必须 setIntent 才能让 Compose 读到新 extras。
        setIntent(intent)
    }

    override fun onDestroy() {
        AudioPlayer.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SCRIPT_CASE = "script_case"
        const val EXTRA_CHARACTER_ID = "character_id"

        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, HomeActivity::class.java))
        }
    }
}
