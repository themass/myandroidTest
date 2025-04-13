package chat.ui.view;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.openai.commonutil.LogUtil;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

public class InputMsgWebView extends WebView {
    Context mContext;

    public InputMsgWebView(Context context) {
        super(context);
        this.mContext = context;
        init();
    }

    public InputMsgWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
        init();
    }

    public InputMsgWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mContext = context;
        init();
    }
    private void init(){
        configureWebViewLikeTextView();
    }
    public void showMarkdown(String markdown) {
        String processedMarkdown = markdown
                .replace("\\(", "&#92;(")
                .replace("\\)", "&#92;)")
                .replace("\\[", "&#92;[")
                .replace("\\]", "&#92;]");
        String htmlContent = convertMarkdownToHtml(processedMarkdown);
        LogUtil.i("htmlContent===="+htmlContent);
        // Step 2: 嵌入 KaTeX 支持
        String finalHtml = loadMarkdownWithLatex(htmlContent).replace("&#92;", "\\");
        LogUtil.i("finalHtml===="+finalHtml);
        // Step 3: 在 WebView 中加载内容
        loadHtmlToWebView(finalHtml);
    }
    private String convertMarkdownToHtml(String markdown) {
        MutableDataSet options = new MutableDataSet();
        // 关键：禁用反斜杠转义
//        options.set(Parser.BACKSLASH_ESCAPES, false);
        options.set(Parser.EXTENSIONS, Arrays.asList(
                StrikethroughExtension.create(),
                TablesExtension.create(),
                AutolinkExtension.create()
        ));

        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        return renderer.render(parser.parse(markdown));
    }

    private String loadMarkdownWithLatex(String htmlContent) {

        // 3. 读取 HTML 模板并注入内容
        String htmlTemplate="";
        try {
            InputStream is = getContext().getAssets().open("katex/incoomingmsg.html");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            htmlTemplate = sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("加载模板失败", e);
        }
        return  htmlTemplate.replace("{{CONTENT}}", htmlContent);

    }
    private void configureWebViewLikeTextView() {
        WebSettings settings = getSettings();
        setPadding(0, 0, 0, 0);
        // 基础配置
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); // 启用 DOM 存储
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE); // 禁用缓存

        // 禁用浏览器特性
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(false); // 关键：禁用宽视口模式

        // 滚动优化
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setScrollBarStyle(WebView.SCROLLBARS_INSIDE_OVERLAY);
        setOverScrollMode(WebView.OVER_SCROLL_NEVER);

        // 背景适配
        setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
        } else {
            setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
        }
        setWebViewClient(new SafeWebViewClient());
    }

    private void loadHtmlToWebView(String html) {
        stopLoading();
        clearHistory();
        clearFormData();
        clearCache(true);
        loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "UTF-8",
                null
        );
    }
    // 加载 HTML 到 WebView
    /**
     * 安全的 WebViewClient 实现
     */
    private static class SafeWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            // 二次检查 KaTeX 渲染
            view.evaluateJavascript(
                    "if (typeof renderMathInElement === 'function') { " +
                            "   renderMathInElement(document.body); " +
                            "} else { " +
                            "   console.error('KaTeX not initialized!'); " +
                            "}", null);
        }
    }
}
