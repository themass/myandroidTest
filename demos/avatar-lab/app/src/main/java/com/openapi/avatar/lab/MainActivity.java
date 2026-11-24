package com.openapi.avatar.lab;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.webkit.ValueCallback;
import androidx.webkit.WebViewAssetLoader;

public final class MainActivity extends Activity {
    private static final String STATE_ID = "selected_engine_id";
    private static final int FILE_CHOOSER_REQUEST = 7041;
    private LinearLayout root, toolbar, body;
    private TextView title, status;
    private WebView webView;
    private String selectedId;
    private ValueCallback<Uri[]> pendingFileResult;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        selectedId = state == null ? null : state.getString(STATE_ID);
        buildShell();
        if (selectedId == null) showCatalog(); else showEngine(EngineCatalog.find(selectedId));
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString(STATE_ID, selectedId);
        super.onSaveInstanceState(out);
    }

    private void buildShell() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xfff5f3ef);
        toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(dp(16), dp(12), dp(16), dp(12)); toolbar.setBackgroundColor(0xff202a35);
        title = new TextView(this); title.setTextColor(0xffffffff); title.setTextSize(18); title.setTypeface(null, 1);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
    }
    private void resetBody() {
        if (customView != null) { root.removeView(customView); customView = null; }
        if (customCallback != null) { customCallback.onCustomViewHidden(); customCallback = null; }
        if (pendingFileResult != null) { pendingFileResult.onReceiveValue(null); pendingFileResult = null; }
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        body.removeAllViews();
    }
    private void showCatalog() {
        selectedId = null; resetBody(); title.setText("数字人对比实验室"); toolbar.removeViews(1, Math.max(0, toolbar.getChildCount()-1));
        ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setPadding(dp(16), dp(10), dp(16), dp(24)); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); body.addView(scroll);
        TextView intro = text("独立 Android 演示目录\n在线网页用于评估参考，不代表引擎已集成。", 15, 0xff48525d); intro.setPadding(0, dp(10), 0, dp(12)); list.addView(intro);
        for (EngineCatalog.Entry e : EngineCatalog.ALL) {
            LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setPadding(dp(16), dp(14), dp(16), dp(14)); item.setBackgroundColor(0xffffffff);
            TextView name = text(e.name, 18, 0xff17212b); name.setTypeface(null, 1); item.addView(name);
            TextView meta = text(e.status, 12, e.kind == EngineCatalog.Kind.LOCAL ? 0xff9a5c13 : 0xff28635b); meta.setPadding(0, dp(4), 0, dp(7)); item.addView(meta);
            item.addView(text(e.description, 14, 0xff48525d));
            item.addView(text("建议观察：" + e.tryList, 13, 0xff344e67));
            TextView source = text("来源 / 许可信息：" + e.source, 12, 0xff687582); source.setPadding(0, dp(7), 0, 0); item.addView(source);
            item.setOnClickListener(v -> showEngine(e));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(10); list.addView(item, lp);
        }
    }
    private void showEngine(EngineCatalog.Entry e) {
        selectedId = e.id; resetBody(); title.setText(e.name);
        toolbar.removeViews(1, Math.max(0, toolbar.getChildCount()-1));
        Button back = button("返回目录"); back.setOnClickListener(v -> showCatalog()); toolbar.addView(back);
        Button retry = button("重试"); retry.setOnClickListener(v -> { if (webView != null) webView.reload(); }); toolbar.addView(retry);
        status = text(e.status, 12, 0xfff0dcae); status.setPadding(dp(12), dp(8), dp(12), dp(8)); status.setBackgroundColor(0xff34424f); body.addView(status);
        webView = new WebView(this); webView.setBackgroundColor(0xfff5f3ef);
        webView.getSettings().setJavaScriptEnabled(true); webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(true); webView.getSettings().setAllowFileAccess(false); webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder().addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        webView.setWebViewClient(new WebViewClient() {
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) { return loader.shouldInterceptRequest(request.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return !safeNavigation(request.getUrl().toString(), e); }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) { status.setText(e.status + " · 正在加载"); }
            @Override public void onPageFinished(WebView view, String url) { status.setText(e.status + " · 页面已就绪（非引擎集成验收）"); }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, android.webkit.WebResourceError error) { if (req.isForMainFrame()) status.setText("加载失败：" + error.getDescription() + "。可点击重试。"); }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest req, android.webkit.WebResourceResponse response) { if (req.isForMainFrame()) status.setText("网页响应异常 HTTP " + response.getStatusCode() + "。可点击重试。"); }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view; customCallback = callback;
                webView.setVisibility(View.GONE);
                root.addView(view, new LinearLayout.LayoutParams(-1, -1));
            }
            @Override public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView); customView = null;
                if (customCallback != null) { customCallback.onCustomViewHidden(); customCallback = null; }
                webView.setVisibility(View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFileResult != null) pendingFileResult.onReceiveValue(null);
                pendingFileResult = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                // VRM 等自定义扩展名的 accept 值不是 MIME；无法解析时退回 */*，避免选择器打不开。
                String[] accept = params.getAcceptTypes();
                if (accept != null && accept.length == 1 && accept[0] != null && accept[0].contains("/")) intent.setType(accept[0]);
                try { startActivityForResult(intent, FILE_CHOOSER_REQUEST); } catch (android.content.ActivityNotFoundException ex) { pendingFileResult=null; callback.onReceiveValue(null); return false; }
                return true;
            }
        });
        body.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        webView.loadUrl(e.url);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && pendingFileResult != null) {
            Uri[] result = resultCode == Activity.RESULT_OK && data != null && data.getData() != null ? new Uri[]{data.getData()} : null;
            pendingFileResult.onReceiveValue(result); pendingFileResult = null;
        }
    }
    private boolean safeNavigation(String url, EngineCatalog.Entry entry) {
        if (entry.kind == EngineCatalog.Kind.LOCAL) return url.startsWith("https://appassets.androidplatform.net/assets/");
        try { java.net.URI u=java.net.URI.create(url); return "https".equals(u.getScheme()); } catch (RuntimeException ex) { return false; }
    }
    private Button button(String label) { Button b=new Button(this); b.setText(label); b.setTextColor(0xffffffff); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff34424f)); b.setAllCaps(false); return b; }
    private TextView text(String value, int size, int color) { TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    @Override public void onBackPressed() {
        if (customView != null) {
            root.removeView(customView); customView = null;
            if (customCallback != null) { customCallback.onCustomViewHidden(); customCallback = null; }
            if (webView != null) webView.setVisibility(View.VISIBLE);
            return;
        }
        if (selectedId != null) showCatalog(); else super.onBackPressed();
    }
    @Override protected void onDestroy() { if (webView != null) { webView.destroy(); webView=null; } super.onDestroy(); }
}
