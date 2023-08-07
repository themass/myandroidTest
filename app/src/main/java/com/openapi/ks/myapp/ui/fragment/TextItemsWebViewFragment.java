package com.openapi.ks.myapp.ui.fragment;

import android.annotation.SuppressLint;
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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.openapi.ks.myapp.bean.vo.FavoriteVo;
import com.openapi.commons.common.ui.base.BaseFragment;
import com.openapi.commons.common.ui.view.MyFavoriteView;
import com.openapi.commons.common.ui.view.MyWebView;
import com.openapi.commons.common.util.FileUtils;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.commons.yewu.net.HttpUtils;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.vo.TextItemsVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.StaticDataUtil;
import com.openapi.ks.myapp.task.SaveTextTask;
import com.openapi.ks.myapp.ui.base.CommonFragmentActivity;
import com.openapi.ks.moviefree1.R;

import butterknife.BindView;

/**
 * Created by openapi on 2016/3/21.
 */
public class TextItemsWebViewFragment extends BaseFragment implements  MyFavoriteView.OnFavoriteItemClick {
    @SuppressLint("HandlerLeak")
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
    @BindView(R.id.my_favoriteview)
    MyFavoriteView myFavoriteView;
    CookieManager cookieManager = null;
    private boolean mFirstPageLoad = true;
    private String url;
    private TextItemsVo vo;
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
        return R.layout.layout_textitem_webview;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mFirstPageLoad = true;
//        flBg.setBackgroundResource(R.drawable.boob_bg2);
        WebView webView = getWebView();
        if (webView != null) {
            init(webView);
            vo = StaticDataUtil.get(Constants.TEXT_FILE, TextItemsVo.class);
            if(vo==null){
                getActivity().finish();
            }
            StaticDataUtil.del(Constants.TEXT_FILE);
            url = vo.fileUrl;
            if (!TextUtils.isEmpty(url)) {
                webView.loadUrl(url);
            }
            myFavoriteView.setListener(this);
            myFavoriteView.initFavoriteBackGroud(url);
//            myFavoriteView.showVideoLocal();
        }
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
//
//    public void canCors() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
//            webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
//        } else {
//            try {
//                Class<?> clazz = webView.getSettings().getClass();
//                Method method = clazz.getMethod("setAllowUniversalAccessFromFileURLs", boolean.class);
//                if (method != null) {
//                    method.invoke(webView.getSettings(), true);
//                }
//            } catch (NoSuchMethodException e) {
//                LogUtil.e(e);
//            } catch (InvocationTargetException e) {
//                LogUtil.e(e);
//            } catch (IllegalAccessException e) {
//                LogUtil.e(e);
//            }
//        }
//    }

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
//
//            @Override
//            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
//                WebResourceResponse response = super.shouldInterceptRequest(view, url);
//                LogUtil.i("资源：" + url);
//                if (url.contains("mybook.css")) {
//                    try {
//                        LogUtil.i("本地css");
//                        response = new WebResourceResponse("text/css", "UTF-8", new StringBufferInputStream(Constants.BOOK_CSS));
//                    } catch (Exception e) {
//                        LogUtil.e(e);
//                    }
//                }
//                return response;
//            }
//
//            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
//            @Override
//            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
//                WebResourceResponse response = super.shouldInterceptRequest(view, request);
//                LogUtil.i("资源：" + request.getUrl().toString());
//                if (request.getUrl().toString().contains("mybook.css")) {
//                    try {
//                        LogUtil.i("本地css");
//                        response = new WebResourceResponse("text/css", "UTF-8", new StringBufferInputStream(Constants.BOOK_CSS));
//                    } catch (Exception e) {
//                        LogUtil.e(e);
//                    }
//                }
//                return response;
//            }

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

    @Override
    public FavoriteVo getFavoriteDataUrl() {
        SaveTextTask.startSave(getActivity(), url);
        return vo.tofavorite();
    }

    @Override
    public String getBrowserDatUrl() {
        return url;
    }
}
