package com.timeline.myapp.ui.base.features;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import com.qq.sexfree.R;
import com.sspacee.common.util.LogUtil;

import com.sspacee.yewu.ads.base.AdmobRewardManger;
import com.timeline.myapp.data.AdsPopStrategy;
import com.timeline.sexfree1.ui.main.MainFragmentViewPage;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by themass on 2016/3/31.
 */
public abstract class TabBaseAdsFragment extends TabBaseFragment implements AdmobRewardManger.OnAdmobRewardListener{
    private static final int ANIM_DURATION_FAB = 400;
    @BindView(R.id.fab_up)
    public FloatingActionButton fabUp;
    private long lastToastShow = 0l;
    private boolean pendingIntroAnimation;

    @OnClick(R.id.fab_up)
    public void onClickFab(View view) {
        if(getActivity() instanceof MainFragmentViewPage){
            ((MainFragmentViewPage)getActivity()).showReward();
        }
    }

    public void next() {
    }
    @Override
    public void onNoRewardAD(){
        AdsPopStrategy.clickAdsShowBtn(getActivity());
    }
//    @Override
//    public boolean onkeyBackDown() {
//        return false;
//    }
//
//    @Override
//    public void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        ((MainFragmentViewPage) getActivity()).addListener(this);
//    }

    @Override
    public void onResume() {
        super.onResume();
        if (pendingIntroAnimation) {
            pendingIntroAnimation = false;
            startIntroAnimation();
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState == null) {
            pendingIntroAnimation = true;
        }
    }

    private void startIntroAnimation() {
        LogUtil.i("fabUp--" + getClass().getSimpleName());
        fabUp.setTranslationY(2 * getResources().getDimensionPixelOffset(R.dimen.btn_fab_size));
        fabUp.animate()
                .translationY(0)
                .setInterpolator(new OvershootInterpolator(1.f))
                .setStartDelay(600)
                .setDuration(ANIM_DURATION_FAB)
                .start();
    }
}
