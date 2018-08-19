package com.qq.ext.util;

import android.app.Activity;
import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.HeaderViewListAdapter;

/**
 * Created by dengt on 14-03-12.
 */
public class ViewUtils {

    /**
     * 通知View重新设置Adapter
     *
     * @param root
     * @param id
     */
    public static void notifyViewResetAdapter(View root, int id) {
        if (root == null) {
            return;
        }
        View view = root.findViewById(id);
        if (view == null) {
            return;
        } else if (view instanceof AdapterView) {
            notifyAdapterViewChanged((AdapterView) view, true);
        } else if (view instanceof ViewPager) {
            notifyViewPagerChanged((ViewPager) view, true);
        }
    }

    /**
     * 通知view更新数据
     *
     * @param root
     * @param id
     */
    public static void notifyViewDatasetChanged(View root, int id) {
        if (root == null) {
            return;
        }
        View view = root.findViewById(id);
        if (view == null) {
            return;
        } else if (view instanceof AdapterView) {
            notifyAdapterViewChanged((AdapterView) view, false);
        } else if (view instanceof ViewPager) {
            notifyViewPagerChanged((ViewPager) view, false);
        }
    }

    /**
     * notify for ViewPager
     *
     * @param viewPager
     */
    private static void notifyViewPagerChanged(ViewPager viewPager, boolean resetAdapter) {
        if (viewPager == null) {
            return;
        }
        PagerAdapter adapter = viewPager.getAdapter();

        if (resetAdapter) {
            viewPager.setAdapter(adapter);
        } else if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * notify for AdapterView
     *
     * @param adapterView
     */
    private static void notifyAdapterViewChanged(AdapterView adapterView, boolean resetAdapter) {
        if (adapterView == null) {
            return;
        }
        Adapter adapter = adapterView.getAdapter();
        if (adapter != null && adapter instanceof HeaderViewListAdapter) {
            adapter = ((HeaderViewListAdapter) adapter).getWrappedAdapter();
        }
        if (adapter == null || !(adapter instanceof BaseAdapter)) {
            return;
        }

        ((BaseAdapter) adapter).notifyDataSetChanged();
    }

    public static int dp2px(Context context, float dp) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    public static int[] getViewLocationOnScreen(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return location;
    }

    @SuppressWarnings("unchecked")
    public static <V extends View> V find(Activity ac, int id) {
        if (ac == null) {
            throw new NullPointerException(" Activity is null");
        }
        return (V) ac.findViewById(id);
    }

    @SuppressWarnings("unchecked")
    public static <V extends View> V find(View view, int id) {

        if (view == null) {
            throw new NullPointerException(" view is null");
        }
        return (V) view.findViewById(id);
    }

    public static void goneView(View _view) {
        if (_view != null && _view.getVisibility() != View.GONE) {
            _view.setVisibility(View.GONE);
        }
    }

    public static void viewVisuble(View _view) {
        if (_view != null && _view.getVisibility() != View.VISIBLE) {
            _view.setVisibility(View.VISIBLE);
        }
    }
}
