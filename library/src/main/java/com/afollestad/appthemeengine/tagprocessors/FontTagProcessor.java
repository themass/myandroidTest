package com.afollestad.appthemeengine.tagprocessors;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.afollestad.appthemeengine.util.TypefaceHelper;

/**
 * @author Aidan Follestad (afollestad)
 */
public class FontTagProcessor extends TagProcessor {

    public static final String PREFIX = "font";

    @Override
    public boolean isTypeSupported(@NonNull View view) {
        return view instanceof TextView;
    }

    @Override
    public void process(@NonNull Context context, @Nullable String key, @NonNull View view, @NonNull String suffix) {
        final TextView tv = (TextView) view;
        tv.setTypeface(TypefaceHelper.get(context, suffix));
    }
}