package com.openapi.common.util;

import android.annotation.SuppressLint;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.TextView;

public class Tools {

    public static void setTextViewLineExtra(final TextView textView, final float add,
                                            final float mult) {
        ViewTreeObserver vto = textView.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @SuppressWarnings("deprecation")
            @SuppressLint("NewApi")
            public void onGlobalLayout() {
                int textLine = textView.getLineCount();
                if (textLine > 0) {
                    if (android.os.Build.VERSION.SDK_INT >=
                            android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        textView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    } else {
                        textView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    }
                    if (textLine > 1) {
                        textView.setLineSpacing(add, mult);
                        // textView.setIncludeFontPadding(false);
                    } else {
                        textView.setLineSpacing(0, 1);
                        // textView.setIncludeFontPadding(true);
                    }
                }
            }
        });
    }
}
