package com.openapi.feature.content.text;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
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

import com.openapi.core.db.FavoriteVo;
import com.openapi.core.legacyui.app.BaseFragment;
import com.openapi.core.favorite.MyFavoriteView;
import com.openapi.common.ui.widget.MyWebView;
import com.openapi.common.util.android.FileUtils;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.ads.base.AdsContext;
import com.openapi.common.net.http.BaseService;
import com.openapi.common.net.http.HttpUtils;
import com.openapi.feature.content.text.TextItemsVo;
import com.openapi.core.config.Constants;
import com.openapi.common.util.android.StaticDataUtil;
import com.openapi.feature.content.text.SaveTextTask;
import com.openapi.core.legacyui.base.CommonFragmentActivity;
import com.openapi.feature.content.R;
import com.openapi.core.legacyui.channel.WebviewProgress;
import com.openapi.core.legacyui.channel.WebviewScreenKt;


/**
 * Created by openapi on 2016/3/21.
 * 内容由 Compose 承载（见 {@link WebviewScreenKt#createWebviewComposeView}）。
 */
public class TextItemsWebViewFragment extends BaseFragment implements MyFavoriteView.OnFavoriteItemClick {
    /** A25：正文接口请求的取消标签 */
    private static final String TEXT_ITEM_TAG = "TEXT_ITEM_TAG";
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
    private BaseService itemService;
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
        // A25：不再叠拉伸背景图——WebView 改实色不透明后，图片拉伸层与正文叠在一起只会糊
        return WebviewScreenKt.createWebviewComposeView(
                requireContext(),
                progress,
                context -> createWebView(),
                context -> createFavorite(),
                null);
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
            loadContent(wv);
        }
        return wv;
    }

    /**
     * A25：`.html` 条目直接 loadUrl 加载的是整站文章页（站点页头、点赞/转发/收藏、
     * 页面自带 JS 抽屉——用户截图里的分类抽屉就是远端页画的），整页重渲染是卡顿根因。
     * 回到旧设计（对齐 {@code TextItemsFragment}）：vo.id 可用时走
     * {@code /text/item.json?id=} 取正文，{@code loadHtmlBody} 渲染纯净 body；
     * 取正文失败再回退整页 {@code loadUrl(fileUrl)}，错误页分支保留。
     * file:// 是离线收藏的本地快照（见 FavoriteFragment），不经接口直接加载。
     */
    private void loadContent(final MyWebView wv) {
        if (url.startsWith("file://") || vo.id == null) {
            wv.loadUrl(url);
            return;
        }
        if (itemService == null) {
            itemService = new BaseService();
            // 用应用级 Context：请求回来时页面可能已销毁，别持有 Fragment/Activity
            itemService.setup(requireContext().getApplicationContext());
        }
        itemService.getDataAsync(
                        Constants.getUrlWithParam(Constants.API_TEXT_ITEM_URL, String.valueOf(vo.id)),
                        TextItemVo.class, TEXT_ITEM_TAG)
                .whenComplete((data, error) -> mHandler.post(() -> {
                    // webView 在 unbindViews 置空：以此判断页面是否还活着
                    if (webView == null || !isAdded()) {
                        return;
                    }
                    if (error == null && data != null && !TextUtils.isEmpty(data.file)) {
                        webView.loadHtmlBody(data.file);
                    } else {
                        LogUtil.w("item.json 取正文失败，回退整页: " + url
                                + (error != null ? " err=" + error : ""));
                        webView.loadUrl(url);
                    }
                }));
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
        // 默认关死本地文件：打开它会让远程页面里的脚本能读取应用沙盒内的 file:// 资源。
        // 例外：离线收藏走 file:///（FavoriteFragment 下载的本站快照），这类地址必须开
        // file access，否则 allowFileAccess=false 直接把本地页拦死（两者互斥，已核对）。
        settings.setAllowFileAccess(url != null && url.startsWith("file://"));
        settings.setLoadWithOverviewMode(true);
        settings.setDomStorageEnabled(true);
//        settings.setAppCacheEnabled(true);
        settings.setGeolocationEnabled(true);
        // 移除已弃用的API调用
        // settings.setGeolocationDatabasePath(getActivity().getCacheDir().toString()); // 已弃用
        settings.setUserAgentString(settings.getUserAgentString() + " " + HttpUtils.getUserAgentSuffix(getActivity()));
        webView.setWebChromeClient(new WebChromeClient());
        // A25：实色不透明背景（原来是 0 全透明，和 Compose 拉伸背景图叠在一起透出杂色）
        webView.setBackgroundColor(Color.WHITE);
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
        if (itemService != null) {
            itemService.cancelRequest(TEXT_ITEM_TAG);
        }
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
