package com.afollestad.appthemeengine.customizers;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.afollestad.appthemeengine.Config;

/**
 * @author Aidan Follestad (afollestad)
 */
public interface ATEToolbarCustomizer {

    @Config.LightToolbarMode
    int getLightToolbarMode(@Nullable Toolbar toolbar);

    @ColorInt
    int getToolbarColor(@Nullable Toolbar toolbar);
}
