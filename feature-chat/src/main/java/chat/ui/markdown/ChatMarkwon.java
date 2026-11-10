package chat.ui.markdown;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.openapi.core.util.ScreenSize;
import com.openapi.core.util.LogUtil;
import com.openapi.feature.chat.R;
import com.openapi.ks.myapp.view.katexplugin.MyJLatexMathPlugin;

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

/**
 * 聊天内 AI 回复等 Markdown + LaTeX 的单一渲染入口（Markwon），避免与 WebView/Flexmark 重复维护。
 */
public final class ChatMarkwon {

    private static volatile Markwon instance;

    private ChatMarkwon() {
    }

    public static Markwon get(Context context) {
        if (instance == null) {
            synchronized (ChatMarkwon.class) {
                if (instance == null) {
                    instance = create(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private static Markwon create(Context ctx) {
        float latexTextSize = ctx.getResources().getDimension(R.dimen.textsize_15);
        return Markwon.builder(ctx)
                .usePlugin(MyJLatexMathPlugin.create(latexTextSize, builder -> {
                    builder.inlinesEnabled(true);
                    com.openapi.ks.myapp.view.katexplugin.JLatexMathTheme.Padding padding =
                            new com.openapi.ks.myapp.view.katexplugin.JLatexMathTheme.Padding(
                                    ScreenSize.dp2px(ctx, 3),
                                    ScreenSize.dp2px(ctx, 3),
                                    ScreenSize.dp2px(ctx, 5),
                                    ScreenSize.dp2px(ctx, 5));
                    builder.theme()
                            .backgroundProvider(new com.openapi.ks.myapp.view.katexplugin.JLatexMathTheme.BackgroundProvider() {
                                @NonNull
                                @Override
                                public Drawable provide() {
                                    return ContextCompat.getDrawable(ctx, R.color.block_groud);
                                }
                            })
                            .textColor(ContextCompat.getColor(ctx, R.color.block_text))
                            .padding(padding);
                    builder.errorHandler(new MyJLatexMathPlugin.ErrorHandler() {
                        @Nullable
                        @Override
                        public Drawable handleError(@NonNull String latex, @NonNull Throwable error) {
                            LogUtil.e("失败：" + latex, error);
                            return ContextCompat.getDrawable(ctx, com.shuyu.gsyvideoplayer.R.drawable.video_error_normal);
                        }
                    });
                }))
                .usePlugin(JLatexMathPlugin.create(latexTextSize, builder -> {
                    builder.inlinesEnabled(true);
                    builder.blocksEnabled(true);
                    JLatexMathTheme.Padding padding = new JLatexMathTheme.Padding(
                            ScreenSize.dp2px(ctx, 3),
                            ScreenSize.dp2px(ctx, 3),
                            ScreenSize.dp2px(ctx, 5),
                            ScreenSize.dp2px(ctx, 5));
                    builder.theme()
                            .backgroundProvider(new JLatexMathTheme.BackgroundProvider() {
                                @NonNull
                                @Override
                                public Drawable provide() {
                                    return ContextCompat.getDrawable(ctx, R.color.block_groud);
                                }
                            })
                            .textColor(ContextCompat.getColor(ctx, R.color.block_text))
                            .padding(padding);
                    builder.errorHandler(new JLatexMathPlugin.ErrorHandler() {
                        @Nullable
                        @Override
                        public Drawable handleError(@NonNull String latex, @NonNull Throwable error) {
                            LogUtil.e("失败：" + latex, error);
                            return ContextCompat.getDrawable(ctx, com.shuyu.gsyvideoplayer.R.drawable.video_error_normal);
                        }
                    });
                }))
                .usePlugin(GlideImagesPlugin.create(ctx))
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(CorePlugin.create())
                .usePlugin(ImagesPlugin.create())
                .usePlugin(HtmlPlugin.create())
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(ctx))
                .build();
    }
}
