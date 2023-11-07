package com.afollestad.appthemeengine.tagprocessors;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.afollestad.appthemeengine.util.ATEUtil;

import java.lang.reflect.Method;

/**
 * @author Aidan Follestad (afollestad)
 */
public class BackgroundTagProcessor extends TagProcessor {

    public static final String PREFIX = "background";

    @Override
    public boolean isTypeSupported(@NonNull View view) {
        return true;
    }

    @Override
    public void process(@NonNull Context context, @Nullable String key, @NonNull View view, @NonNull String suffix) {
        final ColorResult result = getColorFromSuffix(context, key, view, suffix);
        if (result == null) return;
        if (ATEUtil.isInClassPath("androidx.cardview.widget.CardView") &&
                (view.getClass().getName().equalsIgnoreCase("androidx.cardview.widget.CardView") ||
                        view.getClass().getSuperclass().getName().equals("androidx.cardview.widget.CardView"))) {
            try {
                final Class<?> cardViewCls = Class.forName("androidx.cardview.widget.CardView");
                final Method setCardBg = cardViewCls.getMethod("setCardBackgroundColor", Integer.class);
                setCardBg.invoke(view, result.getColor());
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } else {
            view.setBackgroundColor(result.getColor());
        }
    }


}