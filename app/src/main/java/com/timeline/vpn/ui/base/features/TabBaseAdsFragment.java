package com.timeline.vpn.ui.base.features;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.StaticDataUtil;
import com.timeline.vpn.task.ScoreTask;
import com.timeline.vpn.ui.inte.OnBackKeyUpListener;
import com.timeline.vpn.ui.main.MainFragmentViewPage;

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
    private long lastToastShow = 0l;
    private boolean pendingIntroAnimation;

    @OnClick(R.id.fab_up)
    public void onClickFab(View view) {
        next();
        Long lastClickTime = StaticDataUtil.get(Constants.SCORE_CLICK, Long.class, 0l);
        long curent = System.currentTimeMillis();
        long interval = curent - lastClickTime;
        StaticDataUtil.add(Constants.SCORE_CLICK, System.currentTimeMillis());
        if ((interval / 1000) < Constants.SCORE_CLICK_INTERVAL) {
            if ((curent - lastToastShow) / 1000 >= Constants.SCORE_CLICK_INTERVAL) {
                Toast.makeText(getActivity(), R.string.tab_fb_click_fast, Toast.LENGTH_SHORT).show();
                lastToastShow = curent;
            }
            return;
        }
        String msg = getActivity().getResources().getString(R.string.tab_fb_click) + Constants.ADS_SHOW_SCORE;
        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
        ScoreTask.start(getActivity(), Constants.ADS_SHOW_SCORE);
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
        ((MainFragmentViewPage) getActivity()).setListener(this);
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
