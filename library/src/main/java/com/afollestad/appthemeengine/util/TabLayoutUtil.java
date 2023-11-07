package com.afollestad.appthemeengine.util;

import android.view.View;

import com.google.android.material.tabs.TabLayout;

/**
 * @author Aidan Follestad (afollestad)
 */
public final class TabLayoutUtil {

    // External class is used after checking if TabLayout is on the class path. Avoids compile errors.
    public static boolean isTabLayout(View view) {
        return view instanceof TabLayout;
    }

    private TabLayoutUtil() {
    }
}