package com.timeline.vpn.adapter;

import android.view.View;

public interface OnRecyclerViewItemClickListener<T> {
        void onItemClick(View view , T data,int postion);
    }