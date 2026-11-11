package com.openapi.ks.myapp.content.text;

import java.util.concurrent.CompletableFuture;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.openapi.common.util.FileUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.core.view.view.MyFavoriteView;
import com.openapi.common.view.view.MyScrollView;
import com.openapi.common.view.view.MyTextView;
import com.openapi.core.ads.yewu.ads.base.AdsContext;
import com.openapi.core.event.FavoriteVo;
import com.openapi.common.view.view.MyWebView;
import com.openapi.common.net.HttpUtils;
import com.openapi.core.app.AppContext;
import com.openapi.ks.myapp.content.text.TextItemVo;
import com.openapi.ks.myapp.content.text.TextItemsVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.common.net.BaseService;
import com.openapi.common.util.StaticDataUtil;
import com.openapi.ks.myapp.content.text.SaveTextTask;
import com.openapi.ks.myapp.ui.base.CommonFragmentActivity;
import com.openapi.ks.myapp.ui.base.features.LoadableFragment;
import com.openapi.feature.content.R;
import com.openapi.feature.content.databinding.LayoutTextitemsBinding;


/**
 * Created by openapi on 2016/8/12.
 */
public class TextItemsFragment extends LoadableFragment<TextItemVo> implements MyFavoriteView.OnFavoriteItemClick {
    private static final String TEXT_TAG = "TEXT_ITEM_TAG";
    MyWebView webView;
    MyScrollView svText;
    MyFavoriteView myFavoriteView;
    private BaseService indexService;
    private TextItemsVo vo;
    private String infoVo;
    private String url;
    CookieManager cookieManager = null;
    @Override
    protected void bindViews(View view) {
        super.bindViews(view);
        LayoutTextitemsBinding b = LayoutTextitemsBinding.bind(view);
        webView = b.tvView;
        svText = b.svText;
        myFavoriteView = b.myFavoriteview;
    }

    @Override
    protected void unbindViews() {
        webView = null;
        svText = null;
        myFavoriteView = null;
        super.unbindViews();
    }


    public static void startFragment(Context context, TextItemsVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, TextItemsFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.name);
        StaticDataUtil.add(Constants.TEXT_FILE, vo);
        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN1);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
        context.startActivity(intent);
    }
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
//        flBg.setBackgroundResource(R.drawable.boob_bg2);
        WebView webView = getWebView();
        if (webView != null) {
            init(webView);
        }
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        indexService = new BaseService();
        indexService.setup(getActivity());
        vo = StaticDataUtil.get(Constants.TEXT_FILE, TextItemsVo.class);
        url = vo.fileUrl;
        StaticDataUtil.del(Constants.TEXT_FILE);
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        myFavoriteView.setListener(this);
        myFavoriteView.initFavoriteBackGroud(url);
    }

    @Override
    public void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_textitems, parent, true);
    }

    @Override
    protected void onDataLoaded(TextItemVo data) {
        infoVo = data.file;
        webView.loadHtmlBody(infoVo);
//        webView.loadDataWithBaseURL(null,infoVo,"text/html","utf-8",null);
//        tvView.setText(Html.fromHtml(infoVo).toString().trim());
    }

    @Override
    protected CompletableFuture<TextItemVo> loadData(Context context) {
        return indexService.getDataAsync(Constants.getUrlWithParam(Constants.API_TEXT_ITEM_URL, String.valueOf(vo.id)), TextItemVo.class, TEXT_TAG);
    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(TEXT_TAG);
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
    public void setListener(MyWebView.OnTouchRightSlide listener) {
        if (webView != null)
            webView.setListener(listener);
    }

    public WebView getWebView() {
        return webView;
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
                // 使用现代Cookie管理方式
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.flush();
                } else {
                    // 对于旧版本，直接使用CookieManager.flush()
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
}
