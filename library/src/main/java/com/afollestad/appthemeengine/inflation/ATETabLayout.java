package com.afollestad.appthemeengine.inflation;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.afollestad.appthemeengine.ATEActivity;
import com.afollestad.appthemeengine.tagprocessors.ATEDefaultTags;
import com.google.android.material.tabs.TabLayout;

/**
 * @author Aidan Follestad (afollestad)
 */
class ATETabLayout extends TabLayout implements ViewInterface {

    public ATETabLayout(Context context) {
        super(context);
        init(context, null);
    }

    public ATETabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, null);
    }

    public ATETabLayout(Context context, AttributeSet attrs, @Nullable ATEActivity keyContext) {
        super(context, attrs);
        init(context, keyContext);
    }

    private void init(Context context, @Nullable ATEActivity keyContext) {
        ATEDefaultTags.process(this);
        try {
            ATEViewUtil.init(keyContext, this, context);
        } catch (Throwable t) {
            throw new RuntimeException(t.getMessage(), t);
        }
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