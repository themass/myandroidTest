package com.timeline.vpn.ui.base;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

/**
 */
public abstract class PagedAdapter<T> extends BaseAdapter {
    protected LayoutInflater mInflater;
    private Context context;
    private PagedLoader<T> pagedLoader;

    public PagedAdapter(Context context, PagedLoader<T> pagedLoader) {
        this.context = context;
        this.pagedLoader = pagedLoader;
        this.mInflater = LayoutInflater.from(context);
    }


    public PagedLoader<T> getPagedLoader() {
        return pagedLoader;
    }

    @Override
    public int getCount() {
        return pagedLoader.getCount();
    }

    @Override
    public Object getItem(int position) {
        return pagedLoader.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position >= (pagedLoader.getCount() - 1)) {
            pagedLoader.loadNextPage();
        }
        return getPagedItemView(position, convertView, parent);
    }

    public Context getContext() {
        return context;
    }


    protected abstract View getPagedItemView(int position, View convertView, ViewGroup parent);
}
