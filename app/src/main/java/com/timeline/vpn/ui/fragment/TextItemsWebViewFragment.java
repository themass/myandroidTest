package com.timeline.vpn.ui.fragment;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.sspacee.common.ui.base.BaseFragment;
import com.sspacee.common.ui.view.MyWebView;
import com.sspacee.common.util.FileUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.net.HttpUtils;
import com.timeline.vpn.R;
import com.timeline.vpn.base.MyApplication;
import com.timeline.vpn.bean.vo.TextItemsVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.StaticDataUtil;
import com.timeline.vpn.ui.base.CommonFragmentActivity;

import java.io.StringBufferInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import butterknife.BindView;

/**
 * Created by themass on 2016/3/21.
 */
public class TextItemsWebViewFragment extends BaseFragment {
    private static final String CSS = "body{font-size:22px;font-family: 微软雅黑;letter-spacing:2px;line-height:1.5;background-color:rgba(0,0,0,1);}";
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        }
    };
    @BindView(R.id.wv_content)
    MyWebView webView;
    @BindView(R.id.progressbar)
    ProgressBar progressbar;
    @BindView(R.id.fl_bg)
    RelativeLayout flBg;
    CookieManager cookieManager = null;
    private boolean mFirstPageLoad = true;
    private String url;
    private TextItemsVo vo;
    private Runnable run = new Runnable() {
        @Override
        public void run() {
            if (webView != null) {
//                webView.setVisibility(View.VISIBLE);
                setProgressShown(false);
            }
        }
    };

    public static void startFragment(Context context, TextItemsVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, TextItemsWebViewFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.app_name);
        StaticDataUtil.add(Constants.TEXT_FILE, vo);
        intent.putExtra(CommonFragmentActivity.ADS, true);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, true);
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, true);
        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);
        context.startActivity(intent);
    }

    public static String getOutCss(String url) {
        String js = "javascript:var d=document;" +
                "var s=d.createElement('link');" +
                "s.setAttribute('rel', 'stylesheet');" +
                "s.setAttribute('href', '" + url + "');" +
                "d.head.appendChild(s);";
        return js;
    }

    public static String getInnerCss() {
        String js = "javascript:var d=document;" +
                "var s=d.createElement('style');" +
                "s.setAttribute('type', 'text/css');" +
                "s.innerHTML=" + CSS + ";" +
                "d.head.appendChild(s);";
        return js;
    }

    @Override
    protected int getRootViewId() {
        return R.layout.layout_textitem_webview;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mFirstPageLoad = true;
        flBg.setBackgroundResource(R.drawable.boob_bg2);
        WebView webView = getWebView();
        if (webView != null) {
            init(webView);
            vo = StaticDataUtil.get(Constants.TEXT_FILE, TextItemsVo.class);
            StaticDataUtil.del(Constants.TEXT_FILE);
            url = vo.fileUrl;
            if (!TextUtils.isEmpty(url)) {
                webView.loadUrl(url);
            }
        }
    }

    private void setBook() {
        webView.getSettings().setDefaultTextEncodingName("utf-8");
        webView.setBackgroundColor(getActivity().getResources().getColor(R.color.transparent));
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setListener(MyWebView.OnTouchRightSlide listener) {
        if (webView != null)
            webView.setListener(listener);
    }

    public WebView getWebView() {
        return webView;
    }

    private void setProgressShown(boolean shown) {
        if (progressbar != null)
            progressbar.setVisibility(shown ? View.VISIBLE : View.GONE);
    }

    public void canCors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        } else {
            try {
                Class<?> clazz = webView.getSettings().getClass();
                Method method = clazz.getMethod("setAllowUniversalAccessFromFileURLs", boolean.class);
                if (method != null) {
                    method.invoke(webView.getSettings(), true);
                }
            } catch (NoSuchMethodException e) {
                LogUtil.e(e);
            } catch (InvocationTargetException e) {
                LogUtil.e(e);
            } catch (IllegalAccessException e) {
                LogUtil.e(e);
            }
        }
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
        settings.setAppCacheEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setGeolocationDatabasePath(getActivity().getCacheDir().toString());
        settings.setUserAgentString(settings.getUserAgentString() + " " + HttpUtils.getUserAgentSuffix(getActivity()));
        setBook();
        canCors();
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            private String mUrl;

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                setProgressShown(true);
                LogUtil.i("onPageStarted->" + url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                WebResourceResponse response = super.shouldInterceptRequest(view, url);
                LogUtil.i("资源：" + url);
                if (url.contains("mybook.css")) {
                    try {
                        LogUtil.i("本地css");
                        response = new WebResourceResponse("text/css", "UTF-8", new StringBufferInputStream(Constants.BOOK_CSS));
                    } catch (Exception e) {
                        LogUtil.e(e);
                    }
                }
                return response;
            }

            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse response = super.shouldInterceptRequest(view, request);
                LogUtil.i("资源：" + request.getUrl().toString());
                if (request.getUrl().toString().contains("mybook.css")) {
                    try {
                        LogUtil.i("本地css");
                        response = new WebResourceResponse("text/css", "UTF-8", new StringBufferInputStream(Constants.BOOK_CSS));
                    } catch (Exception e) {
                        LogUtil.e(e);
                    }
                }
                return response;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                LogUtil.i("onPageFinished->" + url);
                if (mFirstPageLoad) {
                    mFirstPageLoad = false;
                }
//                webView.loadUrl(getInnerCss());
//               String css =  FileUtils.readAssets(getActivity(),"mybook.css");
//                webView.loadDataWithBaseURL("http://file.ssapcee.com",css,"text/css","utf-8",null);
//                webView.loadUrl(getOutCss("http://file.sspacee.com/file/cache/mybook.css?" + System.currentTimeMillis()));
                mHandler.postDelayed(run, 0);
//                webView.loadUrl("<link rel=\"stylesheet\" href=\"http://file.sspacee.com/file/cache/mybook.css\" type=\"text/css\" /> ");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.flush();
                } else {
                    CookieSyncManager.createInstance(MyApplication.getInstance());
                    CookieSyncManager.getInstance().sync();
                }
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
}
