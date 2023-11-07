package com.afollestad.appthemeengine.util;

import android.view.View;

import androidx.core.widget.NestedScrollView;

/**
 * @author Aidan Follestad (afollestad)
 */
public final class NestedScrollViewUtil {

    // External class is used after checking if NestedScrollView is on the class path. Avoids compile errors.
    public static boolean isNestedScrollView(View view) {
        return view instanceof NestedScrollView;
    }

    private NestedScrollViewUtil() {
    }
}
