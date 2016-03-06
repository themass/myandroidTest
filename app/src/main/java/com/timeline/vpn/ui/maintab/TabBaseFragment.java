package com.timeline.vpn.ui.maintab;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.timeline.vpn.R;
import com.timeline.vpn.ui.base.BaseFragment;

/**
 * Created by gqli on 2015/9/1.
 */
public abstract class TabBaseFragment extends BaseFragment {
    private ViewGroup headerContentView;
    private ViewGroup bodyContentView;

    @Override
    protected int getRootViewId() {
        return R.layout.main_fragment;
    }

    @Override
    protected View setContentView(LayoutInflater inflater) {
        View view = inflater.inflate(getRootViewId(), null);
        headerContentView = (ViewGroup) view.findViewById(R.id.tab_header_content);
        bodyContentView = (ViewGroup) view.findViewById(R.id.tab_body_content);
        inflater.inflate(getTabHeaderViewId(), headerContentView, true);
        inflater.inflate(getTabBodyViewId(), bodyContentView, true);
        return view;
    }

    abstract protected int getTabHeaderViewId();

    abstract protected int getTabBodyViewId();

    public ViewGroup getHeaderContentView() {
        return headerContentView;
    }

    public ViewGroup getBodyContentView() {
        return bodyContentView;
    }
}
