package com.openapi.chat.markdown.katex;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;

/**
 * 补充官方 ext-latex 插件的单美元符号行内公式（{@code $x$}）。
 * 官方插件只识别 {@code $$x$$}，这里注册的 {@link JLatexMathInlineProcessor} 只匹配单个 {@code $}，两者互不重叠。
 * 产生的节点是官方 {@code JLatexMathNode}，渲染由官方插件完成。
 */
public class SingleDollarLatexPlugin extends AbstractMarkwonPlugin {

    @Override
    public void configure(Registry registry) {
        registry.require(MarkwonInlineParserPlugin.class)
                .factoryBuilder()
                .addInlineProcessor(new JLatexMathInlineProcessor());
    }
}
