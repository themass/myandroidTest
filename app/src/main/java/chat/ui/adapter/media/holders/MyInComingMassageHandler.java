package chat.ui.adapter.media.holders;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.openai.commonutil.DensityUtil;
import com.openai.commonutil.LogUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.view.MyIncomingAudioFooterView;
import com.openapi.ks.myapp.view.LaTeXFormulaReplacer;
import com.openapi.ks.myapp.view.katexplugin.MyJLatexMathPlugin;

import chat.ui.data.model.Message;
import de.hdodenhof.circleimageview.CircleImageView;
import io.noties.markwon.Markwon;
import io.noties.markwon.core.CorePlugin;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.ext.latex.JLatexMathTheme;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.image.glide.GlideImagesPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import tz.co.hosannahighertech.messagekit.messages.MessageHolders;

public class MyInComingMassageHandler extends MessageHolders.IncomingTextMessageViewHolder<Message> {
    public TextView messageTextView ;
//    public InputMsgWebView katexView ;
    public CircleImageView messageUserAvatar;
    public Message message;
    public RelativeLayout rvAds;
    public MyIncomingAudioFooterView myIncomingAudioFooterView;

    public MyInComingMassageHandler(View itemView, Object payload) {
        super(itemView, payload);
        messageTextView = itemView.findViewById(R.id.markdownMessageText);
//        katexView = itemView.findViewById(R.id.katexMessageText);
        messageUserAvatar = itemView.findViewById(R.id.messageUserAvatar);
        rvAds = itemView.findViewById(R.id.rv_ads);
        myIncomingAudioFooterView = itemView.findViewById(R.id.ll_sound);
//        WebSettings webSettings = composeView.getSettings();
        // 设置默认字体大小
//        webSettings.setDefaultFontSize(16);
//        composeView.setCurrentConfig(new Config(
//                "file:///android_asset/markdown.css",
//                Config.CssCodeHighlight.MONOKAI_SUBLIME // This can be a custom one too, but there are already added some options.
//        ));
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
        this.message = message;
        // 设置 ComposeView 的内容
        try {
            Markwon markwon = Markwon.builder(messageTextView.getContext())
                    .usePlugin(MyJLatexMathPlugin.create(messageTextView.getContext().getResources().getDimension(R.dimen.textsize_15), builder -> {
                        builder.inlinesEnabled(true);
                        builder.blocksEnabled(false);
                        com.openapi.ks.myapp.view.katexplugin.JLatexMathTheme.Padding padding = new com.openapi.ks.myapp.view.katexplugin.JLatexMathTheme.Padding(DensityUtil.dip2px(messageTextView.getContext(), 3), DensityUtil.dip2px(messageTextView.getContext(), 3), DensityUtil.dip2px(messageTextView.getContext(), 5), DensityUtil.dip2px(messageTextView.getContext(), 5));
                        builder.theme().backgroundProvider(new com.openapi.ks.myapp.view.katexplugin.JLatexMathTheme.BackgroundProvider() {
                                    @NonNull
                                    @Override
                                    public Drawable provide() {
                                        return ContextCompat.getDrawable(messageTextView.getContext(), R.color.block_groud);
                                    }
                                }).textColor(ContextCompat.getColor(messageTextView.getContext(), R.color.block_text))
                                .padding(padding);

                        // @since 4.3.0
                        builder.errorHandler(new MyJLatexMathPlugin.ErrorHandler() {
                            @Nullable
                            @Override
                            public Drawable handleError(@NonNull String latex, @NonNull Throwable error) {
                                // Receive error and optionally return drawable to be displayed instead
                                LogUtil.e("失败：" + latex, error);
                                return ContextCompat.getDrawable(messageTextView.getContext(), com.shuyu.gsyvideoplayer.R.drawable.video_error_normal);
                            }
                        });
                    }))
                    .usePlugin(JLatexMathPlugin.create(messageTextView.getContext().getResources().getDimension(R.dimen.textsize_15), builder -> {
                        builder.inlinesEnabled(true);
                        builder.blocksEnabled(true);
                        JLatexMathTheme.Padding padding = new JLatexMathTheme.Padding(DensityUtil.dip2px(messageTextView.getContext(), 3), DensityUtil.dip2px(messageTextView.getContext(), 3), DensityUtil.dip2px(messageTextView.getContext(), 5), DensityUtil.dip2px(messageTextView.getContext(), 5));
                        builder.theme().backgroundProvider(new JLatexMathTheme.BackgroundProvider() {
                                    @NonNull
                                    @Override
                                    public Drawable provide() {
                                        return ContextCompat.getDrawable(messageTextView.getContext(), R.color.block_groud);
                                    }
                                }).textColor(ContextCompat.getColor(messageTextView.getContext(), R.color.block_text))
                                .padding(padding);
                        // @since 4.3.0
                        builder.errorHandler(new JLatexMathPlugin.ErrorHandler() {
                            @Nullable
                            @Override
                            public Drawable handleError(@NonNull String latex, @NonNull Throwable error) {
                                // Receive error and optionally return drawable to be displayed instead
                                LogUtil.e("失败：" + latex, error);
                                return ContextCompat.getDrawable(messageTextView.getContext(), com.shuyu.gsyvideoplayer.R.drawable.video_error_normal);
                            }
                        });
                    }))

                    .usePlugin(GlideImagesPlugin.create(messageTextView.getContext()))
                    .usePlugin(MarkwonInlineParserPlugin.create())
                    .usePlugin(CorePlugin.create())
                    .usePlugin(ImagesPlugin.create())
                    .usePlugin(HtmlPlugin.create())
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(messageTextView.getContext()))
                    .build();
            //        String msg = LaTeXFormulaReplacer.replaceBrackets(testStr);
            //        LogUtil.i("替换后的字符串::\n"+msg);
            markwon.setMarkdown(messageTextView, message.getText());
            //        katexView.showMarkdown(t);
            messageUserAvatar.setImageResource(R.drawable.ai_chat_1);
            myIncomingAudioFooterView.setMessage(message);
        }catch (Exception e){
            LogUtil.e(e);
        }

//        try {
//            if (rvAds != null) {
//                if (Boolean.TRUE.equals(message.showAds)) {
//                    rvAds.setVisibility(View.VISIBLE);
//                    AdsManager.getInstans().showBannerAds((FragmentActivity) composeView.getContext(), rvAds, AdsContext.Categrey.CATEGREY_VPN2);
//                } else {
//                    rvAds.removeAllViews();
//                    rvAds.setVisibility(View.GONE);
//                }
//            }
//        }catch (Exception e){
//            LogUtil.e("",e);
//        }
    }
    private static final String testStr = "# Markdown 和 LaTeX 混合测试文本\n" +
            "\n" +
            "## 1. Markdown 基础元素\n" +
            "\n" +
            "### 1.1 标题\n" +
            "# 这是一级标题\n" +
            "## 这是二级标题\n" +
            "### 这是三级标题\n" +
            "#### 这是四级标题\n" +
            "##### 这是五级标题\n" +
            "###### 这是六级标题\n" +
            "\n" +
            "### 1.2 段落和文本格式\n" +
            "这是一个普通的段落，包含**加粗**的文本、*斜体*的文本、~~删除线~~的文本和`代码`文本。\n" +
            "\n" +
            "### 1.3 列表\n" +
            "- 无序列表项1\n" +
            "- 无序列表项2\n" +
            "  - 嵌套无序列表项1\n" +
            "  - 嵌套无序列表项2\n" +
            "- 无序列表项3\n" +
            "\n" +
            "1. 有序列表项1\n" +
            "2. 有序列表项2\n" +
            "   1. 嵌套有序列表项1\n" +
            "   2. 嵌套有序列表项2\n" +
            "3. 有序列表项3\n" +
            "\n" +
            "### 1.4 表格\n" +
            "| 表头1 | 表头2 | 表头3 |\n" +
            "|-------|-------|-------|\n" +
            "| 单元格1 | 单元格2 | 单元格3 |\n" +
            "| 单元格4 | 单元格5 | 单元格6 |\n" +
            "| 单元格7 | 单元格8 | 单元格9 |\n" +
            "\n" +
            "### 1.5 链接和图片\n" +
            "这是一个[链接](https://www.example.com)。\n" +
            "这是一个图片链接：![图片](https://via.placeholder.com/150)\n" +
            "\n" +
            "## 2. LaTeX 公式\n" +
            "\n" +
            "### 2.1 行内公式\n" +
            "这是一个行内公式：\\( e^{i\\pi} + 1 = 0 \\)。\n" +
            "这是一个单 `$` 包裹的行内公式：$16\\frac{2}{3}$。\n" +
            "\n" +
            "### 2.2 块级公式\n" +
            "这是一个块级公式：\n" +
            "\\[\n" +
            "\\int_{0}^{1} x^2 \\, dx = \\frac{1}{3}\n" +
            "\\]\n" +
            "\n" +
            "这是一个双 `$$` 包裹的块级公式：\n" +
            "$$\n" +
            "\\frac{a}{b} = c\n" +
            "$$\n" +
            "\n" +
            "### 2.3 使用 \\text{} 插入文本\n" +
            "这是一个包含文本的公式：\n" +
            "\\[\n" +
            "\\text{这是一个文本} \\quad \\text{和公式} \\quad \\frac{a}{b} = c\n" +
            "\\]\n" +
            "\n" +
            "### 2.4 多行公式\n" +
            "这是一个多行公式：\n" +
            "\\[\n" +
            "\\begin{aligned}\n" +
            "a &= b + c \\\\\n" +
            "d &= e + f \\\\\n" +
            "g &= h + i \\\\\n" +
            "\\end{aligned}\n" +
            "\\]\n" +
            "\n" +
            "### 2.5 带条件的公式\n" +
            "这是一个带条件的公式：\n" +
            "\\[\n" +
            "f(x) =\n" +
            "\\begin{cases}\n" +
            "x^2, & \\text{如果 } x \\geq 0 \\\\\n" +
            "-x, & \\text{如果 } x < 0 \\\\\n" +
            "\\end{cases}\n" +
            "\\]\n" +
            "\n" +
            "### 2.6 特殊符号和函数\n" +
            "这是一个包含特殊符号的公式：\n" +
            "\\[\n" +
            "\\alpha, \\beta, \\gamma, \\delta, \\epsilon, \\phi, \\theta, \\lambda, \\pi, \\omega\n" +
            "\\]\n" +
            "\n" +
            "这是一个包含三角函数的公式：\n" +
            "\\[\n" +
            "\\sin^2 \\theta + \\cos^2 \\theta = 1\n" +
            "\\]\n" +
            "\n" +
            "这是一个包含对数函数的公式：\n" +
            "\\[\n" +
            "\\ln(e^x) = x\n" +
            "\\]\n" +
            "\n" +
            "### 2.7 上标和下标\n" +
            "这是一个包含上标和下标的公式：\n" +
            "\\[\n" +
            "a_{1}^{2} + b_{2}^{3} = c_{3}^{4}\n" +
            "\\]\n" +
            "\n" +
            "\n" +
            "### 2.9 代码块中的LaTeX\n" +
            "这是一个代码块中的LaTeX公式：\n" +
            "\\[\n" +
            "\\frac{a}{b} = c\n" +
            "\\]\n" +
            "### 练习1\n" +
            "\n" +
            "**(1) 经过多久后两人第一次相遇？**\n" +
            "\n" +
            "大强和小强的相对速度是 \\(6 \\text{ 米/秒} + 4 \\text{ 米/秒} = 10 \\text{ 米/秒}\\)。\n" +
            "\n" +
            "环形跑道周长为200米，所以两人第一次相遇的时间 \\(t\\) 可以通过以下公式计算：\n" +
            "\\[ t = \\frac{\\text{周长}}{\\text{相对速度}} = \\frac{200 \\text{ 米}}{10 \\text{ 米/秒}} = 20 \\text{ 秒} \\]\n" +
            "\n" +
            "**(2) 再经过多久两人第二次相遇？**\n" +
            "\n" +
            "由于两人背向而行，每次相遇后，他们需要再次跑完一圈才能第二次相遇。因此，第二次相遇的时间与第一次相遇的时间相同，也是20秒。\n" +
            "\n" +
            "### 练习2\n" +
            "\n" +
            "**甲、乙两人多久后第一次相遇？**\n" +
            "\n" +
            "甲和乙的相对速度是 \\(10 \\text{ 米/秒} + 5 \\text{ 米/秒} = 15 \\text{ 米/秒}\\)。\n" +
            "\n" +
            "甲和乙之间的初始距离是100米。他们每跑5秒，就要停10秒休息。我们需要计算他们实际跑步的时间和休息的时间。\n" +
            "\n" +
            "首先，我们计算他们不休息时相遇的时间：\n" +
            "\\[ t_{\\text{不休息}} = \\frac{100 \\text{ 米}}{15 \\text{ 米/秒}} = \\frac{20}{3} \\text{ 秒} \\approx 6.67 \\text{ 秒} \\]\n" +
            "\n" +
            "由于他们每跑5秒就要休息10秒，我们可以计算出他们第一次相遇的总时间。他们需要跑 \\(\\frac{20}{3}\\) 秒，这少于5秒，所以他们不需要休息就能第一次相遇。\n" +
            "\n" +
            "因此，甲和乙两人第一次相遇的总时间是 \\(\\frac{20}{3} \\text{ 秒}\\)。\n" +
            "\n" +
            "综上所述，答案如下：\n" +
            "\n" +
            "\n" +
            "### 练习1\n" +
            "**(1) 经过多久后两人第一次相遇？**\n" +
            "\\[ t = 20 \\text{ 秒} \\]\n" +
            "\n" +
            "**(2) 再经过多久两人第二次相遇？**\n" +
            "\\[ t = 20 \\text{ 秒} \\]\n" +
            "\n" +
            "### 练习2\n" +
            "**甲、乙两人多久后第一次相遇？**\n" +
            "\\[ t = \\frac{20}{3} \\text{ 秒} \\]";
}