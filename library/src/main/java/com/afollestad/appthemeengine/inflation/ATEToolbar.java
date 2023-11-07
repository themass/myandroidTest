package com.afollestad.appthemeengine.inflation;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.afollestad.appthemeengine.ATE;
import com.afollestad.appthemeengine.ATEActivity;
import com.afollestad.appthemeengine.tagprocessors.ATEDefaultTags;

/**
 * @author Aidan Follestad (afollestad)
 */
class ATEToolbar extends Toolbar implements PostInflationApplier, ViewInterface {

    public ATEToolbar(Context context) {
        super(context);
        init(context, null);
    }

    public ATEToolbar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, null);
    }

    public ATEToolbar(Context context, @Nullable AttributeSet attrs, @Nullable ATEActivity keyContext) {
        super(context, attrs);
        init(context, keyContext);
    }

    private String mKey;

    private void init(Context context, @Nullable ATEActivity keyContext) {
        ATEDefaultTags.process(this);
        if (keyContext == null && context instanceof ATEActivity)
            keyContext = (ATEActivity) context;
        if (mKey == null && keyContext != null)
            mKey = keyContext.getATEKey();
    }

    @Override
    public void postApply() {
        ATE.themeView(getContext(), this, mKey);
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