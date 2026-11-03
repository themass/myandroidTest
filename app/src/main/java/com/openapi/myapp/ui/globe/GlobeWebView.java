package com.openapi.myapp.ui.globe;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import com.google.gson.Gson;

import java.util.List;

/**
 * 3D 地球视图：承载 assets/globe/globe.html（globe.gl，离线打包）。
 * <p>
 * 页面通过 https://appassets.androidplatform.net/assets/ 同源加载，避免 file:// 下
 * WebGL 读取贴图被跨域策略拦截。页面未就绪时的调用会暂存，加载完成后补发。
 */
public final class GlobeWebView extends WebView {

    private static final String PAGE_URL = "https://appassets.androidplatform.net/assets/globe/globe.html";

    private final Gson gson = new Gson();
    private boolean pageReady;
    @Nullable
    private String nodesJs;
    @Nullable
    private String userJs;
    @Nullable
    private String exitJs;
    @Nullable
    private String connJs;

    /**
     * 一个节点在地球上的位置。id 与 LocationVo.id 对应。
     * ping 为最近一次测得的延迟（毫秒，负数=超时，null=未测），用于给节点分级上色。
     */
    public static final class GeoNode {
        public final String id;
        public final String name;
        public final double lat;
        public final double lng;
        @Nullable
        public final Integer ping;

        public GeoNode(@NonNull String id, @NonNull String name, double lat, double lng, @Nullable Integer ping) {
            this.id = id;
            this.name = name;
            this.lat = lat;
            this.lng = lng;
            this.ping = ping;
        }
    }

    public GlobeWebView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(Color.TRANSPARENT);
        configure(context);
        loadUrl(PAGE_URL);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void configure(@NonNull Context context) {
        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(context))
                .build();
        setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                replayAll();
            }
        });
        addJavascriptInterface(new Bridge(), "AndroidGlobe");
        // 首页位于 ViewPager 内，横向拖动会被它抢走去切页；按下期间禁止父视图拦截，地球才能拖动旋转。
        setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            boolean finished = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(!finished);
            }
            return false;
        });
    }

    /**
     * 设置所有节点（会替换之前的节点）。
     *
     * @param selectedId 当前选中的节点 id，会被单独放大波纹；没有时传 null
     */
    public void setNodes(@NonNull List<GeoNode> nodes, @Nullable String selectedId) {
        nodesJs = "Globe3D.setNodes(" + gson.toJson(nodes) + "," + gson.toJson(selectedId) + ")";
        run(nodesJs);
    }

    /** 设置用户位置（近似值，用于画连线起点）。label 为地球上的标签文字。 */
    public void setUser(double lat, double lng, @NonNull String label) {
        userJs = "Globe3D.setUser(" + lat + "," + lng + "," + gson.toJson(label) + ")";
        run(userJs);
    }

    /**
     * 标记出口 IP 的位置（网络体检得到的归属地）。与用户位置、节点分开显示，颜色不同。
     * label 例如 "Tokyo"；传 null 表示清除标记。
     */
    public void setExit(@Nullable Double lat, @Nullable Double lng, @Nullable String label) {
        if (lat == null || lng == null) {
            exitJs = "Globe3D.setExit(null,null,null)";
        } else {
            exitJs = "Globe3D.setExit(" + lat + "," + lng + "," + gson.toJson(label) + ")";
        }
        run(exitJs);
    }


    /**
     * 设置连接状态。
     *
     * @param state  idle / connecting / connected / failed
     * @param nodeId 当前目标节点 id；idle 时传 null
     */
    public void setConnection(@NonNull String state, @Nullable String nodeId) {
        connJs = "Globe3D.setConnection(" + gson.toJson(state) + "," + gson.toJson(nodeId) + ")";
        run(connJs);
    }

    private void run(@NonNull String js) {
        if (pageReady) {
            evaluateJavascript(js, null);
        }
    }

    private void replayAll() {
        if (nodesJs != null) {
            evaluateJavascript(nodesJs, null);
        }
        if (userJs != null) {
            evaluateJavascript(userJs, null);
        }
        if (exitJs != null) {
            evaluateJavascript(exitJs, null);
        }
        if (connJs != null) {
            evaluateJavascript(connJs, null);
        }
    }

    /** JS 侧调用 AndroidGlobe.* 的入口。当前只保留接口，节点点击暂不产生动作。 */
    private final class Bridge {
        @JavascriptInterface
        public void onNodeClick(String id) {
            // 预留：点击节点后的选路逻辑由上层决定，这里不擅自切换节点。
        }
    }
}
