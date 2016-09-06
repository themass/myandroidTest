package com.timeline.vpn.ui.user;

import android.os.Bundle;
import android.view.View;

import com.timeline.vpn.R;
import com.timeline.vpn.ui.base.BaseBannerAdsActivity;
import com.timeline.vpn.ui.view.HeartAnimView;

import butterknife.Bind;

/**
 * Created by gqli on 2016/8/13.
 */
public class TestActivity extends BaseBannerAdsActivity {
    @Bind(R.id.v_bglike)
    View bgView;
    @Bind(R.id.iv_like)
    View ivLike;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.anim_heart_view);
        setToolbarTitle(R.string.login);

    }
    public void onClick(View view ){
//        HeartAnim.startAnim(bgView,ivLike);
        new HeartAnimView().show(this,null);
    }


}
