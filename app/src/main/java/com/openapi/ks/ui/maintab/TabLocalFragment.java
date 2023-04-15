package com.openapi.ks.ui.maintab;


import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.openapi.common.util.EventBusUtil;
import com.openapi.ks.free1.R;
import com.openapi.myapp.data.config.PingEvent;
import com.openapi.myapp.ui.base.BannerHeaderFragment;
import com.openapi.myapp.ui.base.features.TabBaseAdsFragment;
import com.openapi.myapp.ui.fragment.LocationPageViewFragment;
import com.openapi.yewu.ads.base.AdsContext;


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
