package com.qq.ks.ui.maintab;


import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.qq.common.util.EventBusUtil;
import com.qq.ks.free1.R;
import com.qq.myapp.data.config.PingEvent;
import com.qq.myapp.ui.base.BannerHeaderFragment;
import com.qq.myapp.ui.base.features.TabBaseAdsFragment;
import com.qq.myapp.ui.fragment.LocationPageViewFragment;
import com.qq.myapp.ui.user.AddCustomeInfoActivity;
import com.qq.yewu.ads.base.AdsContext;


/**
 * Created by dengt on 2015/9/1.
 */
public class TabLocalFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return BannerHeaderFragment.getNewInstans(AdsContext.Categrey.CATEGREY_VPN1);
    }

    @Override
    protected Fragment getTabBodyView(){
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
        super.onClickFab(view);
        EventBusUtil.getEventBus().post(new PingEvent());
    }
}
