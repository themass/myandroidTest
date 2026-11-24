package com.openapi.common.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;


import java.util.HashMap;
import java.util.Map;

public class MyWebView extends WebView {
    Context mContext;
    private OnTouchRightSlide listener;

    public MyWebView(Context context) {
        super(context);
        this.mContext = context;
    }

    public MyWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
    }

    public MyWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mContext = context;
    }

    public void setListener(OnTouchRightSlide listener) {
        this.listener = listener;
    }

    /** 默认 Referer，由应用启动时设置（原来写死在 CommonConstants 里）。 */
    private static String defaultReferer = "";

    public static void setDefaultReferer(String referer) {
        defaultReferer = referer == null ? "" : referer;
    }

    @Override
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        if (additionalHttpHeaders == null) {
            additionalHttpHeaders = new HashMap<>();
        }
        String ref = defaultReferer + "," + url;
        if (additionalHttpHeaders.get(REFERER) != null)
            ref = additionalHttpHeaders.get(REFERER) + "," + defaultReferer;
        additionalHttpHeaders.put(REFERER, ref);
        super.loadUrl(url, additionalHttpHeaders);
    }

    private static final String REFERER = "Referer";

    @Override
    public void loadUrl(String url) {
        this.loadUrl(url, null);
    }

    public void loadHtmlBody(String htmlBody){
        // 1. 构造一段自适应的 CSS
        String css = "<style>\n" +
                "  body{word-wrap:break-word;margin:0;padding:0;}\n" +
                "  img,video{max-width:100%;height:auto;}\n" +
                "</style>";

        // 2. 拼成完整 HTML
        String html = "<html><head>"
                + "<meta name=\"viewport\" "
                + "content=\"width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1,user-scalable=no\" />"
                + css
                + "</head><body>"
                + htmlBody
                + "</body></html>";

        // 3. 加载
        loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    public interface OnTouchRightSlide {
        void onTouchRight(int distans);
    }
}
