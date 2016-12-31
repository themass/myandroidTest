package com.timeline.vpn.ui.maintab;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.task.ScoreTask;
import com.timeline.vpn.ui.main.MainFragment;

import butterknife.Bind;
import butterknife.OnClick;

/**
 * Created by themass on 2016/3/31.
 */
public abstract class TabBaseAdsFragment extends TabBaseFragment implements OnBackKeyUpListener {
    private static final int ANIM_DURATION_FAB = 400;
    @Bind(R.id.fab_up)
    public FloatingActionButton fabUp;
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
        }
    };
    private boolean pendingIntroAnimation;

    @OnClick(R.id.fab_up)
    public void onClickFab(View view) {
//        boolean firstClick = PreferenceUtils.getPrefBoolean(getActivity(), Constants.FIRST_FB_CLICK,true);
//        new HeartAnimView().show(getActivity());
//        if(firstClick) {
//            PreferenceUtils.setPrefBoolean(getActivity(),Constants.FIRST_FB_CLICK,false);
        ScoreTask.start(getActivity(), Constants.ADS_SHOW_SCORE);
        String msg = getActivity().getResources().getString(R.string.tab_fb_click) + Constants.ADS_SHOW_SCORE;
        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();

//        }
        next();
    }

    public void next() {
        AdsAdview.interstitialAds(getActivity(), mHandler);
    }

    @Override
    public boolean onkeyBackUp() {
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setHasOptionsMenu(true);
        ((MainFragment) getActivity()).setListener(this);
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
