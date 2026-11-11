package com.openapi.core.legacyui.features;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.openapi.core.legacyui.app.BaseFragment;
import com.openapi.common.ui.widget.MyWebView;
import com.openapi.common.util.android.FileUtils;
import com.openapi.common.util.android.IpUtil;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.net.HttpUtils;
import com.openapi.core.R;
import com.openapi.core.app.AppContext;
import com.openapi.core.config.Constants;
import com.openapi.core.legacyui.channel.WebviewProgress;
import com.openapi.core.legacyui.channel.WebviewScreenKt;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by openapi on 2016/3/21.
 * 内容由 Compose 承载（见 {@link WebviewScreenKt#createWebviewComposeView}）。
 */
public class BaseWebViewFragment extends BaseFragment {
    public static final String PARAM_WEB_VIEW_CAN_GO_BACK = "PARAM_WEB_VIEW_CAN_GO_BACK";
    public static final String TAG_FROM = "BaseWebViewFragment_TAG_FROM";
    public static final int TAG_FROM_DEFAULT = 0x0000;
    MyWebView webView;
    CookieManager cookieManager = null;
    private boolean mFirstPageLoad = true;
    private WebViewListener webViewListener;
    private int from;
    private String url;
    private final WebviewProgress progress = new WebviewProgress();

    @Override
    protected int getRootViewId() {
        // 内容由 setContentView 直接提供 ComposeView，此布局 id 不再使用
        return 0;
    }

    @Override
    protected View setContentView(LayoutInflater inflater) {
        Bundle args = getArguments();
        if (args != null) {
            from = args.getInt(TAG_FROM, TAG_FROM_DEFAULT);
        }
        url = getParamUrl();
        return WebviewScreenKt.createWebviewComposeView(
                requireContext(),
                progress,
                context -> createWebView(),
                null,
                null);
    }

    @Override
    protected void unbindViews() {
        webView = null;
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

    public boolean goForward() {
        WebView webView = getWebView();
        if (webView != null && webView.canGoForward()) {
            webView.goForward();
            return true;
        }
        return false;
    }

    public boolean goBack() {
        WebView webView = getWebView();
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        LogUtil.i("canGoBack--" + webView.canGoBack());
        return false;
    }

    public void setListener(MyWebView.OnTouchRightSlide listener) {
        if (webView != null)
            webView.setListener(listener);
    }

    private String getParamUrl() {
        Bundle args = getArguments();
        if (args != null) {
            String url = args.getString(Constants.URL);
            if (!TextUtils.isEmpty(url)) {
                return url;
            }
        }
        Activity activity = getActivity();
        if (activity == null) {
            return null;
        }
        return activity.getIntent().getStringExtra(Constants.URL);
    }

    public WebView getWebView() {
        return webView;
    }

    private void setProgressShown(boolean shown) {
        progress.setShown(shown);
    }

    /**
     * 设置 cookies
     *
     * @param cookiesPath 请求地址
     */
    public void setCookies(String cookiesPath) {
        Map<String, String> cookieMap = new HashMap<>();
        String domain = IpUtil.getDomain(cookiesPath);
        String cookie = AppContext.get().getSharedPreferences("cookie", Context.MODE_PRIVATE).getString(domain, "");// 从SharedPreferences中获取整个Cookie串
        if (!TextUtils.isEmpty(cookie)) {
            String[] cookieArray = cookie.split(";");// 多个Cookie是使用分号分隔的
            for (int i = 0; i < cookieArray.length; i++) {
                int position = cookieArray[i].indexOf("=");// 在Cookie中键值使用等号分隔
                String cookieName = cookieArray[i].substring(0, position);// 获取键
                String cookieValue = cookieArray[i].substring(position + 1);// 获取值
                String value = cookieName + "=" + cookieValue;// 键值对拼接成 value
                LogUtil.i("cookie", value);
                CookieManager.getInstance().setCookie(domain, value);// 设置 Cookie
            }
        }
    }

    @SuppressLint({"JavascriptInterface", "SetJavaScriptEnabled"})
    private void init(final WebView webView) {
        cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false); // 如果需要自动播放媒体

        settings.setDatabaseEnabled(false);
        settings.setSaveFormData(false);
        settings.setBuiltInZoomControls(false);
        settings.setSupportZoom(false); // 可以缩放
        settings.setUseWideViewPort(false);
        settings.setAllowFileAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setDomStorageEnabled(true);
//        settings.setAppCacheEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setGeolocationDatabasePath(getActivity().getCacheDir().toString());
        settings.setUserAgentString(settings.getUserAgentString() + " " + HttpUtils.getUserAgentSuffix(getActivity()));
        String cacheDirPath = AppContext.get().getFilesDir().getAbsolutePath() + "cache/";
        LogUtil.i("ua=" + settings.getUserAgentString());
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            private String mUrl;

            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                //setCookies(url);
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                setProgressShown(true);
                LogUtil.i("onPageStarted->" + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                setProgressShown(false);
                LogUtil.i("onPageFinished->" + url);
                if (mFirstPageLoad) {
                    mFirstPageLoad = false;
                }
                if (webViewListener != null) {
                    webViewListener.onPageFinished();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.flush();
                } else {
                    CookieSyncManager.createInstance(AppContext.get());
                    CookieSyncManager.getInstance().sync();
                }
                //saveCookie(url);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                LogUtil.i("onLoadResource->" + url);
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
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // 处理权限请求
                request.grant(request.getResources());
            }
        });
    }

    public void setWebViewListener(WebViewListener webViewListener) {
        this.webViewListener = webViewListener;
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
    public void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }

    public interface WebViewListener {
        void onPageFinished();
    }
}
