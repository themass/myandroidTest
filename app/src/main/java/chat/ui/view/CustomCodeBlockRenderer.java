package chat.ui.view;

import com.vladsch.flexmark.html.HtmlWriter;
import com.vladsch.flexmark.html.renderer.NodeRenderer;
import com.vladsch.flexmark.html.renderer.NodeRendererContext;
import com.vladsch.flexmark.html.renderer.NodeRendererFactory;
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.sequence.BasedSequence;

import java.util.Collections;
import java.util.Set;

public class CustomCodeBlockRenderer implements NodeRenderer {
    // 必须添加 Factory 内部类
    public static class Factory implements NodeRendererFactory {
        @Override
        public NodeRenderer apply(DataHolder options) {
            return new CustomCodeBlockRenderer(); // 创建渲染器实例
        }
    }

    @Override
    public Set<NodeRenderingHandler<?>> getNodeRenderingHandlers() {
        return Collections.singleton(new NodeRenderingHandler<>(
                com.vladsch.flexmark.ast.FencedCodeBlock.class,
                this::renderFencedCodeBlock
        ));
    }

    private void renderFencedCodeBlock(
            com.vladsch.flexmark.ast.FencedCodeBlock node,
            NodeRendererContext context,
            HtmlWriter html
    ) {
        BasedSequence info = node.getInfo();
        String language = info.isNotNull() ? info.toString().trim() : "";

        // 1. 生成 <pre> 标签
        html.withAttr()
                .tag("pre");

        // 2. 设置 <code> 的 class 属性
        html.attr("class", "language-" + (language.isEmpty() ? "none" : language))
                .withAttr()
                .tag("code");  // 生成 <code class="language-xxx">

        // 3. 写入代码内容
        html.text(node.getContentChars());

        // 4. 关闭标签（顺序：先关闭 code，再关闭 pre）
        html.closeTag("code"); // </code>
        html.closeTag("pre"); // </pre>
    }
}