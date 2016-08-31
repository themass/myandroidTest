package com.timeline.vpn.ui.vpn;

import android.os.Bundle;

import com.timeline.vpn.R;
import com.timeline.vpn.ui.base.BaseBannerAdsActivity;

/**
 * Created by gqli on 2016/8/12.
 */
public class LocationChooseaActivity extends BaseBannerAdsActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_location_choose);
        setToolbarTitle(R.string.location_choose_title);
    }
}
