package com.afollestad.appthemeengine.util;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/**
 * @author Aidan Follestad (afollestad)
 */
public final class RecyclerViewUtil {

    // External class is used after checking if RecyclerView is on the class path. Avoids compile errors.
    public static boolean isRecyclerView(View view) {
        return view instanceof RecyclerView;
    }

    private RecyclerViewUtil() {
    }
}
