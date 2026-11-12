package com.openapi.feature.photosolve.markdown;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;

/**
 * 补充官方 ext-latex 插件不支持的单个美元符号行内公式（{@code $x$}）。
 * 与 :feature-chat 里的同名类是同一份实现，feature 之间不能互相依赖，所以各留一份。
 *
 * 官方插件只识别 {@code $$x$$}；这里注册的 {@link SingleDollarMathProcessor} 只匹配单个 {@code $}，两者不重叠。
 * 产生的节点仍是官方 {@code JLatexMathNode}，由官方插件负责绘制。
 */
public class SingleDollarLatexPlugin extends AbstractMarkwonPlugin {

    @Override
    public void configure(Registry registry) {
        registry.require(MarkwonInlineParserPlugin.class)
                .factoryBuilder()
                .addInlineProcessor(new SingleDollarMathProcessor());
    }
}
