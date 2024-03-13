package com.openapi.ks.myapp.ui.base.features;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import com.openapi.commons.common.ui.base.BaseFragment;
import com.openapi.commons.common.ui.view.MyWebView;
import com.openapi.commons.common.util.FileUtils;
import com.openapi.commons.common.util.IpUtil;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.Utils;
import com.openapi.commons.yewu.net.HttpUtils;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.urlparser.UrlParser;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;

/**
 * Created by openapi on 2016/3/21.
 */
public class BaseWebViewFragment extends BaseFragment {
    public static final String PARAM_WEB_VIEW_CAN_GO_BACK = "PARAM_WEB_VIEW_CAN_GO_BACK";
    public static final String TAG_FROM = "BaseWebViewFragment_TAG_FROM";
    public static final int TAG_FROM_DEFAULT = 0x0000;
    @BindView(R.id.wv_content)
    MyWebView webView;
    @BindView(R.id.progressbar)
    ProgressBar progressbar;
    CookieManager cookieManager = null;
    private boolean mFirstPageLoad = true;
    private WebViewListener webViewListener;
    private int from;
    private String url;

    @Override
    protected int getRootViewId() {
        return R.layout.base_webview_layout;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mFirstPageLoad = true;
        if (getArguments() != null) {
            from = getArguments().getInt(TAG_FROM, TAG_FROM_DEFAULT);
        }
        WebView webView = getWebView();
        if (webView != null) {
            init(webView);
            url = getParamUrl();
            if (!TextUtils.isEmpty(url)) {
                webView.loadUrl(url);
            }
        }
    }

    private void setBook() {
        webView.getSettings().setDefaultTextEncodingName("utf-8");
        webView.setBackgroundColor(0); // 设置背景色
        webView.getBackground().setAlpha(0); // 设置填充透明度 范围：0-255
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
        if (progressbar != null)
            progressbar.setVisibility(shown ? View.VISIBLE : View.GONE);
    }
//初始化webView 时调用
    /**
     * 设置 cookies
     * @param cookiesPath 请求地址
     */
    public void setCookies(String cookiesPath) {
        Map<String, String> cookieMap = new HashMap<>();
        String domain = IpUtil.getDomain(cookiesPath);
        String cookie = MyApplication.getInstance().getSharedPreferences("cookie", Context.MODE_PRIVATE).getString(domain, "");// 从SharedPreferences中获取整个Cookie串
        if (!TextUtils.isEmpty(cookie)) {
            String[] cookieArray = cookie.split(";");// 多个Cookie是使用分号分隔的
            for (int i = 0; i < cookieArray.length; i++) {
                int position = cookieArray[i].indexOf("=");// 在Cookie中键值使用等号分隔
                String cookieName = cookieArray[i].substring(0, position);// 获取键
                String cookieValue = cookieArray[i].substring(position + 1);// 获取值
                String value = cookieName + "=" + cookieValue;// 键值对拼接成 value
                LogUtil.i("cookie", value);
                CookieManager.getInstance().setCookie( domain, value);// 设置 Cookie
            }
        }
    }
//stop 生命周期调用
    /**
     * 保存 Cookie
     */
    private void saveCookie(String path) {
            String domain = IpUtil.getDomain(path);
            CookieManager cookieManager = CookieManager.getInstance();
            String cookieStr = cookieManager.getCookie(domain);
            LogUtil.i(cookieStr);
            SharedPreferences preferences = MyApplication.getInstance().getSharedPreferences("cookie", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(domain, cookieStr);
            editor.commit();
    }
    @SuppressLint({"JavascriptInterface", "SetJavaScriptEnabled"})
    private void init(final WebView webView) {
        cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
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
        String cacheDirPath = MyApplication.getInstance().getFilesDir().getAbsolutePath()+"cache/";
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
                    CookieSyncManager.createInstance(MyApplication.getInstance());
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
