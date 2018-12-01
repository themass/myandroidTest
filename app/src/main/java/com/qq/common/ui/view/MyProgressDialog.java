package com.qq.common.ui.view;

import android.app.Dialog;
import android.content.Context;

import com.qq.fq2.R;

public class MyProgressDialog extends Dialog {

    public MyProgressDialog(Context context) {
        super(context, R.style.dialog);
        init();
    }

    public MyProgressDialog(Context context, int theme) {
        super(context, theme);
        init();
    }

    private void init() {
        setContentView(R.layout.layout_progress);
        setCancelable(true);
        setCanceledOnTouchOutside(false);
    }

}  