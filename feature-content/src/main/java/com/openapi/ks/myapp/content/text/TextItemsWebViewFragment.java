package com.openapi.ks.myapp.content.text;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.openapi.core.event.FavoriteVo;
import com.openapi.ks.myapp.ui.base.app.BaseFragment;
import com.openapi.core.view.view.MyFavoriteView;
import com.openapi.common.view.view.MyWebView;
import com.openapi.common.util.android.FileUtils;
import com.openapi.common.util.log.LogUtil;
import com.openapi.core.ads.yewu.ads.base.AdsContext;
import com.openapi.common.net.HttpUtils;
import com.openapi.ks.myapp.content.text.TextItemsVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.common.util.android.StaticDataUtil;
import com.openapi.ks.myapp.content.text.SaveTextTask;
import com.openapi.ks.myapp.ui.base.CommonFragmentActivity;
import com.openapi.feature.content.R;
import com.openapi.ks.myapp.ui.channel.WebviewProgress;
import com.openapi.ks.myapp.ui.channel.WebviewScreenKt;


/**
 * Created by openapi on 2016/3/21.
 * 内容由 Compose 承载（见 {@link WebviewScreenKt#createWebviewComposeView}）。
 */
public class TextItemsWebViewFragment extends BaseFragment implements MyFavoriteView.OnFavoriteItemClick {
    @SuppressLint("HandlerLeak")
    protected Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
        }
    };
    MyWebView webView;
    MyFavoriteView myFavoriteView;
    CookieManager cookieManager = null;
    private boolean mFirstPageLoad = true;
    private String url;
    private TextItemsVo vo;
    private final WebviewProgress progress = new WebviewProgress();

    private Runnable run = new Runnable() {
        @Override
        public void run() {
            if (webView != null) {
                setProgressShown(false);
            }
        }
    };

    public static void startFragment(Context context, TextItemsVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, TextItemsWebViewFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.app_name);
        StaticDataUtil.add(Constants.TEXT_FILE, vo);
        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);
        intent.putExtra(CommonFragmentActivity.BANNER_NEED_GONE, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN3);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
        context.startActivity(intent);
    }

    @Override
    protected int getRootViewId() {
        // 内容由 setContentView 直接提供 ComposeView，此布局 id 不再使用
        return 0;
    }

    @Override
    protected View setContentView(LayoutInflater inflater) {
        mFirstPageLoad = true;
        vo = StaticDataUtil.get(Constants.TEXT_FILE, TextItemsVo.class);
        StaticDataUtil.del(Constants.TEXT_FILE);
        if (vo == null) {
            // 原实现此处直接解引用 vo 会崩溃，这里改为关闭页面
            getActivity().finish();
            return null;
        }
        url = vo.fileUrl;
        return WebviewScreenKt.createWebviewComposeView(
                requireContext(),
                progress,
                context -> createWebView(),
                context -> createFavorite(),
                R.drawable.boob_bg2);
    }

    @Override
    protected void unbindViews() {
        webView = null;
        myFavoriteView = null;
        super.unbindViews();
    }

    private MyWebView createWebView() {
        MyWebView wv = new MyWebView(requireContext());
        webView = wv;
        init(wv);
        if (!TextUtils.isEmpty(url)) {
            wv.loadUrl(url);
        }
        return wv;
    }

    private View createFavorite() {
        MyFavoriteView fav = new MyFavoriteView(requireContext());
        myFavoriteView = fav;
        fav.setListener(this);
        fav.initFavoriteBackGroud(url);
        return fav;
    }

    public void setListener(MyWebView.OnTouchRightSlide listener) {
        if (webView != null)
            webView.setListener(listener);
    }

    public WebView getWebView() {
        return webView;
    }

    private void setProgressShown(boolean shown) {
        progress.setShown(shown);
    }

    @SuppressLint({"JavascriptInterface", "SetJavaScriptEnabled"})
    private void init(final WebView webView) {
        cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        // 移除已弃用的API调用
        // settings.setDatabaseEnabled(false); // 已弃用
        // settings.setSaveFormData(false); // 已弃用
        settings.setBuiltInZoomControls(false);
        settings.setSupportZoom(false); // 可以缩放
        settings.setUseWideViewPort(false);
        settings.setAllowFileAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setDomStorageEnabled(true);
//        settings.setAppCacheEnabled(true);
        settings.setGeolocationEnabled(true);
        // 移除已弃用的API调用
        // settings.setGeolocationDatabasePath(getActivity().getCacheDir().toString()); // 已弃用
        settings.setUserAgentString(settings.getUserAgentString() + " " + HttpUtils.getUserAgentSuffix(getActivity()));
        webView.setWebChromeClient(new WebChromeClient());
        webView.setBackgroundColor(0);
        webView.setWebViewClient(new WebViewClient() {
            private String mUrl;

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                setProgressShown(true);
                LogUtil.i("onPageStarted->" + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                LogUtil.i("onPageFinished->" + url);
                if (mFirstPageLoad) {
                    mFirstPageLoad = false;
                }
                mHandler.postDelayed(run, 0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.flush();
                } else {
                    // 使用现代Cookie管理方式
                    cookieManager.flush();
                }
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                //LogUtil.i("onLoadResource->" + url);
                mUrl = url;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                LogUtil.e("onReceivedError->" + error.toString());
                if (url.equals(mUrl)) {
                    String errorPage = FileUtils.readAssets(getActivity(), "error_page.html");
                    errorPage = errorPage.replace("####", url);
                    webView.loadData(errorPage, "text/html; charset=UTF-8", null);
                }
            }
        });

    }

    @Override
    public void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    public void onDestroyView() {
        mHandler.removeCallbacks(run);
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroyView();
    }

    @Override
    public FavoriteVo getFavoriteDataUrl() {
        SaveTextTask.startSave(getActivity(), url);
        return vo.tofavorite();
    }

    @Override
    public String getBrowserDataUrl() {
        return url;
    }
}
