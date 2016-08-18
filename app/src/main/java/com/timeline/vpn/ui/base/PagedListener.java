package com.timeline.vpn.ui.base;

import android.widget.AdapterView;

/**
 * @author jrzheng 2014-05-08.
 */
public interface PagedListener extends AdapterView.OnItemClickListener {
    void onPreLoad();

    void onLoaded();
}
