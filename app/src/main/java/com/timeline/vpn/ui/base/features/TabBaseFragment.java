package com.timeline.vpn.ui.base.features;


import android.app.Fragment;
import android.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.timeline.vpn.R;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.ui.base.log.BaseFragment;

/**
 * Created by themass on 2015/9/1.
 */
public abstract class TabBaseFragment extends BaseFragment {
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
        Fragment header = getTabHeaderView();
        if ( header!= null) {
            FragmentManager fm = getFragmentManager();
            Fragment fragment =  fm.findFragmentById(R.id.fl_header);
            if(fragment==null){
                fm.beginTransaction().replace(R.id.fl_header, header).commitAllowingStateLoss();
            }

        } else {
            headerContentView.setVisibility(View.GONE);
        }
        Fragment body = getTabBodyView();
        if (body != null) {
            FragmentManager fm = getFragmentManager();
            Fragment fragment =  fm.findFragmentById(R.id.fl_content);
            if(fragment==null){
                fm.beginTransaction().replace(R.id.fl_content, body).commitAllowingStateLoss();
            }

        }
        setUp(bodyContentView, inflater);
        return view;
    }

    abstract protected Fragment getTabHeaderView();

    abstract protected Fragment getTabBodyView();

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
