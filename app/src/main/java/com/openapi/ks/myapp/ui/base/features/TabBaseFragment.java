package com.openapi.ks.myapp.ui.base.features;


import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.openapi.commons.common.ui.base.BaseFragment;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.ks.moviefree1.R;


/**
 * Created by openapi on 2015/9/1.
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
        FragmentManager fm = getChildFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.fl_header);
        if (fragment == null) {
            Fragment header = getTabHeaderView();
            if (header == null) {
                headerContentView.setVisibility(View.GONE);
            } else {
                fm.beginTransaction().replace(R.id.fl_header, header).commitAllowingStateLoss();
            }
        }
        fragment = fm.findFragmentById(R.id.fl_content);
        if (fragment == null) {
            Fragment body = getTabBodyView();
            fm.beginTransaction().replace(R.id.fl_content, body).commitAllowingStateLoss();
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