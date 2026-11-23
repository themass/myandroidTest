package com.openapi.feature.photosolve.markdown;

import androidx.annotation.Nullable;

import org.commonmark.node.Node;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.ext.latex.JLatexMathNode;
import io.noties.markwon.inlineparser.InlineProcessor;

/**
 * 匹配行内公式 {@code $...$}（不匹配 {@code $$...$$}，那是块公式，交给官方插件）。
 *
 * 额外做一步：吃掉公式内部的换行。模型偶尔会把公式排版成多行，
 * 而块公式（{@code $$}）在多行时一旦缺一个美元符号就会整段渲染失败，这里的选择是宁可把它当行内公式。
 */
public class SingleDollarMathProcessor extends InlineProcessor {

    private static final Pattern RE = Pattern.compile("(?<!\\$)\\$([^$\\n]+)\\$(?!\\$)");

    @Override
    public char specialCharacter() {
        return '$';
    }

    @Nullable
    @Override
    protected Node parse() {
        final String matched = match(RE);
        if (matched == null) {
            return null;
        }
        final JLatexMathNode node = new JLatexMathNode();
        node.latex(matched.substring(1, matched.length() - 1).trim());
        return node;
    }
}
