package com.timeline.vpn.ui.base;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import com.timeline.vpn.R;
import com.timeline.vpn.common.net.HttpUtils;
import com.timeline.vpn.common.util.FileUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.view.MyWebView;

import butterknife.Bind;

/**
 * Created by themass on 2016/3/21.
 */
public class BaseWebViewFragment extends BaseFragment {
    public static final String PARAM_WEB_VIEW_CAN_GO_BACK = "PARAM_WEB_VIEW_CAN_GO_BACK";
    public static final String TAG_FROM = "BaseWebViewFragment_TAG_FROM";
    public static final int TAG_FROM_DEFAULT = 0x0000;
    @Bind(R.id.wv_content)
    MyWebView webView;
    @Bind(R.id.progressbar)
    ProgressBar progressbar;
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

    @SuppressLint({"JavascriptInterface", "SetJavaScriptEnabled"})
    private void init(final WebView webView) {
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
        settings.setAppCacheEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setGeolocationDatabasePath(getActivity().getCacheDir().toString());
        settings.setUserAgentString(settings.getUserAgentString() + " " + HttpUtils.getUserAgentSuffix(getActivity()));
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                setProgressShown(true);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                setProgressShown(false);
                if (mFirstPageLoad) {
                    mFirstPageLoad = false;
                }
                if (webViewListener != null) {
                    webViewListener.onPageFinished();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                String errorPage = FileUtils.readAssets(getActivity(), "error_page.html");
                errorPage = errorPage.replace("####", url);
                webView.loadData(errorPage, "text/html; charset=UTF-8", null);
            }
        });
    }

    public void setWebViewListener(WebViewListener webViewListener) {
        this.webViewListener = webViewListener;
    }

    @Override
    public void onPause() {
        super.onPause();
        if(webView!=null) {
            webView.onPause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(webView!=null) {
            webView.destroy();
        }
    }

    public interface WebViewListener {
        public void onPageFinished();
    }
}
