package com.openapi.myapp.ui.base.features;

import android.os.Bundle;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import com.openapi.common.util.LogUtil;
import com.openapi.ks.free1.R;
import com.openapi.myapp.data.AdsPopStrategy;
import com.openapi.myapp.ui.inte.OnBackKeyDownListener;
import com.openapi.ks.ui.main.MainFragmentViewPage;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.AdsManager;
import com.openapi.yewu.ads.admob.AdmobRewardAds;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by dengt on 2016/3/31.
 */
public abstract class TabBaseAdsFragment extends TabBaseFragment implements OnBackKeyDownListener , AdmobRewardAds.OnAdmobRewardListener{
    private static final int ANIM_DURATION_FAB = 400;
    @BindView(R.id.fab_up)
    public FloatingActionButton fabUp;
    private boolean pendingIntroAnimation;
    public AdmobRewardAds admobRewardAds;
    @OnClick(R.id.fab_up)
    public void onClickFab(View view) {
        LogUtil.i("onClickFab -- tabbase" + getClass().getName());
        LogUtil.i(getActivity().getClass().getName());
        if(getActivity() instanceof MainFragmentViewPage){
            ((MainFragmentViewPage)getActivity()).showReward();
        }else {
            admobRewardAds.showAd();
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
        AdsManager.getInstans().showInterstitialAds(getActivity(), AdsContext.Categrey.CATEGREY_VPN1, false,AdsContext.AdsFrom.MOBVISTA,1);
//        AdsPopStrategy.clickAdsShowBtn(getActivity());
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((MainFragmentViewPage) getActivity()).addListener(this);
        admobRewardAds = new AdmobRewardAds(getActivity(),this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pendingIntroAnimation) {
            pendingIntroAnimation = false;
            startIntroAnimation();
        }
        admobRewardAds.onAdResume();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState == null) {
            pendingIntroAnimation = true;
        }
    }

    private void startIntroAnimation() {
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
        admobRewardAds.onAdPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        admobRewardAds.onAdDestroy();
        super.onDestroy();
    }
}
