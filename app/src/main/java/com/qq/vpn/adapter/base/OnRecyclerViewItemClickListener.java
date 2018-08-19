package com.qq.vpn.adapter.base;

import android.view.View;

public interface OnRecyclerViewItemClickListener<T> {
        void onLongItemClick(View view, int position);

        void onItemClick(View view, int position);
    }
