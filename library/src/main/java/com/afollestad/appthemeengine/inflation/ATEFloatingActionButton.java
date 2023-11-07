package com.afollestad.appthemeengine.inflation;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.afollestad.appthemeengine.ATEActivity;
import com.afollestad.appthemeengine.tagprocessors.ATEDefaultTags;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * @author Aidan Follestad (afollestad)
 */
public class ATEFloatingActionButton extends FloatingActionButton implements ViewInterface {

    public ATEFloatingActionButton(Context context) {
        super(context);
        init(context, null);
    }

    public ATEFloatingActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, null);
    }

    public ATEFloatingActionButton(Context context, AttributeSet attrs, @Nullable ATEActivity keyContext) {
        super(context, attrs);
        init(context, keyContext);
    }

    private void init(Context context, @Nullable ATEActivity keyContext) {
        ATEDefaultTags.process(this);
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
