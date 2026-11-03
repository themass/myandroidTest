package com.openapi.ks.ui.maintab;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.openapi.common.util.EventBusUtil;
import com.openapi.common.util.PreferenceUtils;
import com.openapi.ks.free1.R;
import com.openapi.myapp.base.MyApplication;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.config.PingEvent;
import com.openapi.myapp.ui.base.BannerHeaderFragment;
import com.openapi.myapp.ui.base.features.TabBaseAdsFragment;
import com.openapi.myapp.ui.fragment.LocationPageViewFragment;
import com.openapi.myapp.ui.fragment.VpnRadFragment;
import com.openapi.yewu.ads.base.AdsContext;


/**
 * Created by dengt on 2015/9/1.
 */
public class TabLocalFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return new VpnRadFragment();
//        return BannerHeaderFragment.getNewInstans(AdsContext.Categrey.CATEGREY_VPN1);
    }

    @Override
    protected View setContentView(LayoutInflater inflater) {
        if (!PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), Constants.NEW_HOME_LAYOUT, true)) {
            return super.setContentView(inflater);
        }
        // 新版首页：不走 TabBaseFragment 的 CollapsingToolbarLayout，地球固定铺满，不随滚动移出屏幕。
        View view = inflater.inflate(R.layout.home_header_container, null);
        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentById(R.id.fl_header) == null) {
            fm.beginTransaction().replace(R.id.fl_header, getTabHeaderView()).commitAllowingStateLoss();
        }
        return view;
    }

    @Override
    protected Fragment getTabBodyView(){
        // 新版首页：节点列表已是独立页面（卡片点击进入），首页下半部分不再嵌列表。
        if (PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), Constants.NEW_HOME_LAYOUT, true)) {
            return new Fragment();
        }
        LocationPageViewFragment f = new LocationPageViewFragment();
        f.putSerializable(Boolean.FALSE);
        return f;
    }
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fabUp.setVisibility(View.VISIBLE);
        fabUp.setImageResource(R.drawable.ic_ping_clear_b);
    }
    @Override
    public void onClickFab(View view) {
//        super.onClickFab(view);
        EventBusUtil.getEventBus().post(new PingEvent());
    }
}
