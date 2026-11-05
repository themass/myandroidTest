package com.openapi.ks.myapp.ui.base.features;

import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.openapi.core.util.LogUtil;
import com.openapi.ks.chat.R;
import com.openapi.core.ads.yewu.ads.AdsPopStrategy;
import com.openapi.ks.myapp.ui.user.SettingActivity;

/**
 * Created by openapi on 2016/3/31.
 */
public abstract class TabBaseAdsFragment extends TabBaseFragment {
    private static final int ANIM_DURATION_FAB = 400;
    public FloatingActionButton fabUp;
    private long lastToastShow = 0l;
    private boolean pendingIntroAnimation;

    /** 子类重写时必须先调用 super：悬浮按钮来自 TabBaseFragment 的 base_fragment 布局。 */
    @Override
    protected void bindViews(View view) {
        super.bindViews(view);
        fabUp = view.findViewById(R.id.fab_up);
        fabUp.setOnClickListener(this::onClickFab);
    }

    @Override
    protected void unbindViews() {
        fabUp = null;
        super.unbindViews();
    }

    public void onClickFab(View view) {
        startActivity(SettingActivity.class);
//        AdsPopStrategy.clickAdsShowBtn(getActivity());
    }

    public void next() {
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
