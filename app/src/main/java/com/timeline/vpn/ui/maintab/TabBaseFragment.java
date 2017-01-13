package com.timeline.vpn.ui.maintab;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.timeline.vpn.R;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.ui.base.BaseFragment;

/**
 * Created by themass on 2015/9/1.
 */
public abstract class TabBaseFragment extends BaseFragment {
    public static int NULL_VIEW = -1;
    private ViewGroup headerContentView;
    private ViewGroup bodyContentView;

    @Override
    protected int getRootViewId() {
        return R.layout.base_fragment;
    }

    @Override
    protected View setContentView(LayoutInflater inflater) {
        View view = inflater.inflate(getRootViewId(), null);
        headerContentView = (ViewGroup) view.findViewById(R.id.fl_header);
        bodyContentView = (ViewGroup) view.findViewById(R.id.fl_content);
        if (getTabHeaderViewId() != NULL_VIEW) {
            inflater.inflate(getTabHeaderViewId(), headerContentView, true);
        } else {
            headerContentView.setVisibility(View.GONE);
        }
        if (getTabBodyViewId() != NULL_VIEW) {
            inflater.inflate(getTabBodyViewId(), bodyContentView, true);
        }
        setUp(bodyContentView, inflater);
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

    public void setUp(View view, LayoutInflater inflater) {
        LogUtil.i(getClass().getSimpleName() + "-setUp");
    }
}
