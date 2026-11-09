package com.openapi.ks.myapp.view.katexplugin;

import org.commonmark.node.CustomBlock;

public class JLatexMathBlock extends CustomBlock {

    private String latex;

    public String latex() {
        return latex;
    }

    public void latex(String latex) {
        this.latex = latex;
    }
}
