package com.timeline.vpn.ui.inte;

import android.widget.AdapterView;

/**
 */
public interface PagedListener extends AdapterView.OnItemClickListener {
    void onPreLoad();

    void onLoaded();
}
