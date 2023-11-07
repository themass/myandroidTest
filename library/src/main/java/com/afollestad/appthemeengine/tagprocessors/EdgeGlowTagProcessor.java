package com.afollestad.appthemeengine.tagprocessors;

import android.content.Context;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.afollestad.appthemeengine.util.ATEUtil;
import com.afollestad.appthemeengine.util.EdgeGlowUtil;
import com.afollestad.appthemeengine.util.NestedScrollViewUtil;
import com.afollestad.appthemeengine.util.RecyclerViewUtil;
import com.afollestad.appthemeengine.util.ViewPagerUtil;

/**
 * @author Aidan Follestad (afollestad)
 */
public class EdgeGlowTagProcessor extends TagProcessor {

    public static final String NESTEDSCROLLVIEW_CLASS = "androidx.core.widget.NestedScrollView";
    public static final String RECYCLERVIEW_CLASS = "androidx.recyclerview.widget.RecyclerView";
    public static final String VIEWPAGER_CLASS = "androidx.viewpager.widget.ViewPager";

    public static final String PREFIX = "edge_glow";

    @Override
    public boolean isTypeSupported(@NonNull View view) {
        return view instanceof ScrollView ||
                view instanceof AbsListView ||
                (ATEUtil.isInClassPath(NESTEDSCROLLVIEW_CLASS) && NestedScrollViewUtil.isNestedScrollView(view)) ||
                (ATEUtil.isInClassPath(RECYCLERVIEW_CLASS) && RecyclerViewUtil.isRecyclerView(view)) ||
                (ATEUtil.isInClassPath(VIEWPAGER_CLASS) && ViewPagerUtil.isViewPager(view));
    }

    @Override
    public void process(@NonNull Context context, @Nullable String key, @NonNull View view, @NonNull String suffix) {
        final ColorResult result = getColorFromSuffix(context, key, view, suffix);
        if (result == null)
            return;
        EdgeGlowUtil.setEdgeGlowColorAuto(view, result.getColor());
    }
}