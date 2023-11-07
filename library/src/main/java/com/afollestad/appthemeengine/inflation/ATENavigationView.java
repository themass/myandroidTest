package com.afollestad.appthemeengine.inflation;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.afollestad.appthemeengine.ATEActivity;
import com.google.android.material.navigation.NavigationView;

/**
 * @author Aidan Follestad (afollestad)
 */
class ATENavigationView extends NavigationView implements ViewInterface {

    public ATENavigationView(Context context) {
        super(context);
        init(context, null);
    }

    public ATENavigationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, null);
    }

    public ATENavigationView(Context context, AttributeSet attrs, @Nullable ATEActivity keyContext) {
        super(context, attrs);
        init(context, keyContext);
    }

    private void init(Context context, @Nullable ATEActivity keyContext) {
        ATEViewUtil.init(keyContext, this, context);
    }

    @Override
    public boolean setsStatusBarColor() {
        return false;
    }

    @Override
    public boolean setsToolbarColor() {
        return false;
    }
}