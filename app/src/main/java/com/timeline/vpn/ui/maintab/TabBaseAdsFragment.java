package com.timeline.vpn.ui.maintab;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import com.timeline.vpn.R;
import com.timeline.vpn.ads.AdsStrategy;
import com.timeline.vpn.ads.DeafultAdsStrategy;
import com.timeline.vpn.ads.interstitial.InterstitialAdsController;
import com.timeline.vpn.ads.interstitial.InterstitialProxy;
import com.timeline.vpn.bean.vo.AdsStrategyVo;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.StaticDataUtil;
import com.timeline.vpn.ui.main.MainFragment;

import butterknife.Bind;
import butterknife.OnClick;

/**
 * Created by gqli on 2016/3/31.
 */
public abstract class TabBaseAdsFragment extends TabBaseFragment implements OnBackKeyUpListener{
    private static final int ANIM_DURATION_FAB = 400;
    @Bind(R.id.fab_up)
    public FloatingActionButton fabUp;
    private InterstitialProxy proxy;
    InterstitialAdsController controller;
    private boolean pendingIntroAnimation;
    protected Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
            switch (msg.what){
                case Constants.ADS_NO_MSG:
                    next();
                    break;
                case Constants.ADS_CLICK_MSG:
                    break;
                case Constants.ADS_PRESENT_MSG:
                    break;
                case Constants.ADS_DISMISS_MSG:
                    break;
                default:
                    break;
            }
        }
    };
    @OnClick(R.id.fab_up)
    public void onClickFab(View view){
        next();
    }
    public void next(){
        controller = proxy.getController();
        if(controller!=null){
            controller.showAds();
        }
    }
    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
    }

    @Override
    public boolean onkeyBackUp() {
        if(controller!=null){
            controller.close();
        }
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setHasOptionsMenu(true);
        proxy = new InterstitialProxy(new AdsStrategy(StaticDataUtil.get(Constants.STORY_ADSSTATEGY, AdsStrategyVo.class).interstitial, DeafultAdsStrategy.DEAFULT_INTERSTITIAL),getActivity(),mHandler);
        ((MainFragment)getActivity()).setListener(this);
    }
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
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
}
