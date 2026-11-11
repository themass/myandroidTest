package com.openapi.chat.markdown.katex;

import androidx.annotation.Nullable;

import com.openapi.common.util.log.LogUtil;

import org.commonmark.node.Node;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.ext.latex.JLatexMathNode;
import io.noties.markwon.inlineparser.InlineProcessor;

/**
 * @since 4.3.0
 */
class JLatexMathInlineProcessor extends InlineProcessor {

    private static final Pattern RE = Pattern.compile("(?<!\\$)\\$([^$]+)\\$(?!\\$)");

    @Override
    public char specialCharacter() {
        return '$';
    }

    @Nullable
    @Override
    protected Node parse() {

        final String latex = match(RE);
        if (latex == null) {
            return null;
        }

        final io.noties.markwon.ext.latex.JLatexMathNode node = new JLatexMathNode();
        node.latex(latex.substring(1, latex.length() - 1));
        return node;
    }
}
