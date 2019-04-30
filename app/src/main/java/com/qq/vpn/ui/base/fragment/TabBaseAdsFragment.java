package com.qq.vpn.ui.base.fragment;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import com.qq.Constants;
import com.qq.ads.reward.AdmobRewardManger;
import com.qq.ext.util.LogUtil;
import com.qq.ext.util.SystemUtils;
import com.qq.network.R;
import com.qq.vpn.support.AdsPopStrategy;
import com.qq.vpn.ui.inte.OnBackKeyDownListener;
import com.qq.vpn.main.MainActivity;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by dengt on 2016/3/31.
 */
public abstract class TabBaseAdsFragment extends TabBaseFragment implements OnBackKeyDownListener , AdmobRewardManger.OnAdmobRewardListener{
    private static final int ANIM_DURATION_FAB = 400;
    @BindView(R.id.fab_up)
    public FloatingActionButton fabUp;
    private long lastToastShow = 0l;
    private boolean pendingIntroAnimation;
    @OnClick(R.id.fab_up)
    public void onClickFab(View view) {
        if(getActivity() instanceof MainActivity){
            ((MainActivity)getActivity()).showReward();
        }
    }

    public void next() {
    }

    @Override
    public boolean onkeyBackDown() {
        return false;
    }
    @Override
    public void onNoRewardAD(){
        AdsPopStrategy.clickAdsShowBtn(getActivity());
    }
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
        if(Constants.MYPOOL.equals(Constants.NetWork.uc)) {
            fabUp.setVisibility(View.VISIBLE);
        }else{
            fabUp.setVisibility(View.GONE);
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