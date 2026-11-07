package com.openapi.core.util;

import android.app.Activity;
import android.content.Context;

import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.HeaderViewListAdapter;

/**
 * Created by wjying on 13-12-2.
 */
@SuppressWarnings("unchecked")
public class ViewUtils {



    public static <V extends View> V find(Activity ac, int id) {
        if (ac == null) {
            throw new NullPointerException(" Activity is null");
        }
        View view = ac.findViewById(id);
        if (view == null) {
            return null;
        }
        // 安全的类型转换，运行时检查
        try {
            return (V) view;
        } catch (ClassCastException e) {
            throw new ClassCastException("View with id " + id + " cannot be cast to expected type: " + 
                view.getClass().getSimpleName());
        }
    }

    public static <V extends View> V find(View view, int id) {
        if (view == null) {
            throw new NullPointerException(" view is null");
        }
        View foundView = view.findViewById(id);
        if (foundView == null) {
            return null;
        }
        // 安全的类型转换，运行时检查
        try {
            return (V) foundView;
        } catch (ClassCastException e) {
            throw new ClassCastException("View with id " + id + " cannot be cast to expected type: " + 
                foundView.getClass().getSimpleName());
        }
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
