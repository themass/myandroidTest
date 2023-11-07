package com.afollestad.appthemeengine.util;

import android.view.View;

import androidx.viewpager.widget.ViewPager;

/**
 * @author Aidan Follestad (afollestad)
 */
public final class ViewPagerUtil {

    // External class is used after checking if ViewPager is on the class path. Avoids compile errors.
    public static boolean isViewPager(View view) {
        return view instanceof ViewPager;
    }

    private ViewPagerUtil() {
    }
}
