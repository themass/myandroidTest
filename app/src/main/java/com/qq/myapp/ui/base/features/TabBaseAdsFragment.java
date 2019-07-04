package com.qq.myapp.ui.base.features;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import com.qq.common.util.LogUtil;
import com.qq.fq2.R;
import com.qq.myapp.data.AdsPopStrategy;
import com.qq.myapp.ui.inte.OnBackKeyDownListener;
import com.qq.vpn.ui.main.MainFragmentViewPage;
import com.qq.yewu.ads.base.AdsContext;
import com.qq.yewu.ads.base.AdsManager;
import com.qq.yewu.ads.reward.AdmobRewardManger;

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
    public AdmobRewardManger admobRewardManger;
    @OnClick(R.id.fab_up)
    public void onClickFab(View view) {
        if(getActivity() instanceof MainFragmentViewPage){
            ((MainFragmentViewPage)getActivity()).showReward();
        }
//        admobRewardManger.showAd();
    }

    public void next() {
    }

    @Override
    public boolean onkeyBackDown() {
        return false;
    }
    @Override
    public void onNoRewardAD(){
        AdsManager.getInstans().showInterstitialAds(getActivity(), AdsContext.Categrey.CATEGREY_VPN1, false,AdsContext.AdsFrom.MOBVISTA,1);
//        AdsPopStrategy.clickAdsShowBtn(getActivity());
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((MainFragmentViewPage) getActivity()).addListener(this);
//        admobRewardManger = new AdmobRewardManger(getActivity(),this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pendingIntroAnimation) {
            pendingIntroAnimation = false;
            startIntroAnimation();
        }
//        admobRewardManger.onAdResume();
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

    @Override
    public void onPause() {
//        admobRewardManger.onAdPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
//        admobRewardManger.onAdDestroy();
        super.onDestroy();
    }
}
