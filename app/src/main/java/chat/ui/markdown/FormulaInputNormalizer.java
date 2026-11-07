package chat.ui.markdown;

/**
 * 统一处理模型/API 常见输出，减轻 LaTeX 定界符与反斜杠被多转义导致无法渲染的问题。
 */
public final class FormulaInputNormalizer {

    private FormulaInputNormalizer() {
    }

    /**
     * 在交给 Flexmark/KaTeX 或 Markwon 之前调用。
     * 将 <code>\\(</code>、<code>\\[</code> 等「多了一层反斜杠」的定界符逐步收束为 KaTeX auto-render 与 Markwon 能识别的形式。
     */
    public static String forRender(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw == null ? "" : raw;
        }
        String s = raw;
        // 循环收敛：JSON/流式里可能出现 \\\\( 等多层转义
        for (int i = 0; i < 4; i++) {
            String next = collapseDelimiterEscapes(s);
            if (next.equals(s)) {
                break;
            }
            s = next;
        }
        return s;
    }

    private static String collapseDelimiterEscapes(String s) {
        return s.replace("\\\\[", "\\[")
                .replace("\\\\]", "\\]")
                .replace("\\\\(", "\\(")
                .replace("\\\\)", "\\)");
    }
}
