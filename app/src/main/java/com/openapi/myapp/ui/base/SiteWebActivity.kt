package com.openapi.myapp.ui.base

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.openapi.ks.free1.R
import com.openapi.myapp.constant.Constants
import com.openapi.myapp.ui.base.features.BaseWebViewFragment
import com.openapi.yewu.ads.base.AdsContext
import com.openapi.yewu.ads.base.AdsManager

/**
 * Sites 的“全屏网页”样式：顶部横栏（左：返回键，右：banner 广告位），下面整屏 WebView。
 * 返回键先回退网页历史，没有历史才关闭页面。
 *
 * banner 的显示与隐藏沿用 [WebViewActivity]：由 Sites 项的 adsShow 决定，
 * 页面可见时显示，离开时移除（避免后台继续请求广告）。
 *
 * 与原有的 [WebViewActivity]（带标题栏和菜单）并存，由设置页的开关选择用哪一个。
 */
class SiteWebActivity : AppCompatActivity() {

    private lateinit var flBanner: ViewGroup
    private var adsNeed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_site_web)
        enterImmersive()

        flBanner = findViewById(R.id.fl_banner)
        adsNeed = intent.getBooleanExtra(Constants.ADS_SHOW_CONFIG, false)
        findViewById<TextView>(R.id.site_title).text = intent.getStringExtra(Constants.TITLE).orEmpty()

        if (savedInstanceState == null) {
            val fragment = BaseWebViewFragment()
            fragment.arguments = Bundle().apply {
                putString(Constants.URL, intent.getStringExtra(Constants.URL).orEmpty())
            }
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment, fragment)
                .commit()
        }

        findViewById<ComposeView>(R.id.site_back).setContent {
            BackKey(onBack = { onBackPressedDispatcher.onBackPressed() })
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val web = supportFragmentManager.findFragmentById(R.id.fragment) as? BaseWebViewFragment
                if (web != null && web.goBack()) {
                    return
                }
                // 没有网页历史：关闭本页。先关掉自己的回调，避免再次进入这里。
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    /** 沉浸式全屏：隐藏状态栏和导航栏；从边缘滑动时临时显示，松手后自动隐藏。 */
    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** 从其它窗口（如通知栏）切回来时，系统栏会重新出现，这里再隐藏一次。 */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    override fun onResume() {
        super.onResume()
        showAds()
    }

    override fun onPause() {
        super.onPause()
        hideAds()
    }

    private fun showAds() {
        if (adsNeed) {
            flBanner.visibility = View.VISIBLE
            AdsManager.getInstans().showBannerAds(
                this, flBanner, AdsContext.Categrey.CATEGREY_VPN1, AdsContext.AdsFrom.ADMOB,
            )
        } else {
            flBanner.visibility = View.GONE
        }
    }

    private fun hideAds() {
        flBanner.removeAllViews()
        flBanner.visibility = View.GONE
    }

    companion object {
        @JvmStatic
        fun start(context: Context, url: String, title: String?, adsShow: Boolean) {
            val intent = Intent(context, SiteWebActivity::class.java)
            intent.putExtra(Constants.URL, url)
            intent.putExtra(Constants.TITLE, title)
            intent.putExtra(Constants.ADS_SHOW_CONFIG, adsShow)
            context.startActivity(intent)
        }
    }
}

/**
 * 左侧返回键：占满横栏高度，居中；图标与其它页面的返回键同形。
 * 横栏配色：底色用卡片色（比页面背景略亮，形成顶栏），图标纯白，底边分割线同列表。
 */
@Composable
private fun BackKey(onBack: () -> Unit) {
    Box(
        modifier = Modifier.size(56.dp).background(ComposeTheme.Card),
        contentAlignment = Alignment.Center,
    ) {
        ComposeBackButton(onBack = onBack, color = ComposeTheme.TextPrimary, pagePadding = 8.dp)
    }
}
