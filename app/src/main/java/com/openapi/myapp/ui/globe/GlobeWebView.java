package com.openapi.myapp.ui.globe;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
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

    /** 长按节点的回调（在主线程调用）。 */
    public interface OnNodeLongPressListener {
        void onNodeLongPress(@NonNull String nodeId);
    }

    /**
     * 单击节点的回调（在主线程调用）。
     * <p>
     * 上层用它做"点一下就连这个节点"：地球是首页最大的视觉元素，点下去只画一条虚线
     * 而不连，等于把最大的入口浪费掉。长按仍然是进列表看详情，两者分工不同。
     */
    public interface OnNodeClickListener {
        void onNodeClick(@NonNull String nodeId);
    }

    private final Gson gson = new Gson();
    @Nullable
    private OnNodeLongPressListener longPressListener;
    @Nullable
    private OnNodeClickListener clickListener;
    private boolean pageReady;
    @Nullable
    private String nodesJs;
    @Nullable
    private String userJs;
    @Nullable
    private String exitJs;
    @Nullable
    private String connJs;
    @Nullable
    private String trafficJs;
    private long lastTraffic;

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
        // 长按由页面自己识别（见 globe.html 的 onPointClick），这里消费掉 WebView 的长按，避免弹出系统菜单。
        setOnLongClickListener(v -> true);
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

    /**
     * 实时吞吐（字节/秒），只影响连线的动画速度和粗细。
     * <p>
     * 每秒调一次即可：地球是"我在被保护"的视觉证据，数据在动时线就该跑起来，
     * 闲时几乎静止——这比任何文字都直观。
     */
    public void setTraffic(long bytesPerSecond) {
        // 变化很小时不重绘：每秒一次 evaluateJavascript + Three.js 重建数据代价不小，
        // 而且人眼也看不出 1KB/s 和 2KB/s 的区别
        long v = Math.max(0, bytesPerSecond);
        if (Math.abs(v - lastTraffic) < 4096) {
            return;
        }
        lastTraffic = v;
        trafficJs = "Globe3D.setTraffic(" + v + ")";
        run(trafficJs);
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
        if (trafficJs != null) {
            evaluateJavascript(trafficJs, null);
        }
    }

    public void setOnNodeLongPressListener(@Nullable OnNodeLongPressListener listener) {
        this.longPressListener = listener;
    }

    public void setOnNodeClickListener(@Nullable OnNodeClickListener listener) {
        this.clickListener = listener;
    }

    /** JS 侧调用 AndroidGlobe.* 的入口。 */
    private final class Bridge {
        @JavascriptInterface
        public void onNodeClick(String id) {
            // 同样是 JS 桥接线程，切回主线程再通知上层
            post(() -> {
                if (clickListener != null) {
                    clickListener.onNodeClick(id);
                }
            });
        }

        @JavascriptInterface
        public void onNodeLongPress(final String id) {
            // JS 桥接在后台线程调用：切到主线程，震动并交给上层跳转。
            post(() -> {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (longPressListener != null) {
                    longPressListener.onNodeLongPress(id);
                }
            });
        }
    }
}
