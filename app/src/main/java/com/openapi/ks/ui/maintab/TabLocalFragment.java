package com.openapi.ks.ui.maintab;


import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.openapi.common.util.EventBusUtil;
import com.openapi.common.util.PreferenceUtils;
import com.openapi.ks.free1.R;
import com.openapi.myapp.base.MyApplication;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.config.PingEvent;
import com.openapi.myapp.ui.base.BannerHeaderFragment;
import com.openapi.myapp.ui.base.features.TabBaseAdsFragment;
import com.openapi.myapp.ui.fragment.LocationPageViewFragment;
import com.openapi.myapp.ui.fragment.VpnRadFragment;
import com.openapi.yewu.ads.base.AdsContext;


/**
 * Created by dengt on 2015/9/1.
 */
public class TabLocalFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return new VpnRadFragment();
//        return BannerHeaderFragment.getNewInstans(AdsContext.Categrey.CATEGREY_VPN1);
    }

    @Override
    protected View setContentView(LayoutInflater inflater) {
        if (!PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), Constants.NEW_HOME_LAYOUT, true)) {
            return super.setContentView(inflater);
        }
        // 新版首页：不走 TabBaseFragment 的 CollapsingToolbarLayout，地球固定铺满，不随滚动移出屏幕。
        View view = inflater.inflate(R.layout.home_header_container, null);
        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentById(R.id.fl_header) == null) {
            fm.beginTransaction().replace(R.id.fl_header, getTabHeaderView()).commitAllowingStateLoss();
        }
        return view;
    }

    @Override
    protected Fragment getTabBodyView(){
        // 新版首页：节点列表已是独立页面（卡片点击进入），首页下半部分不再嵌列表。
        if (PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), Constants.NEW_HOME_LAYOUT, true)) {
            return new Fragment();
        }
        LocationPageViewFragment f = new LocationPageViewFragment();
        f.putSerializable(Boolean.FALSE);
        return f;
    }
    private static final String KEY_PING_X = "PING_FAB_X";
    private static final String KEY_PING_Y = "PING_FAB_Y";

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fabUp.setVisibility(View.VISIBLE);
        fabUp.setImageResource(R.drawable.ic_ping_clear_b);
        restorePingPosition();
        makePingDraggable();
    }

    /** 恢复用户上次拖动后的位置（以 translation 保存，相对于默认位置的偏移）。 */
    private void restorePingPosition() {
        Context context = getActivity();
        if (context == null) {
            return;
        }
        fabUp.setTranslationX(parseOffset(PreferenceUtils.getPrefString(context, KEY_PING_X, "0")));
        fabUp.setTranslationY(parseOffset(PreferenceUtils.getPrefString(context, KEY_PING_Y, "0")));
    }

    private static float parseOffset(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    /**
     * Ping 球可以拖动，松手后位置记住。移动距离很小时视为点击，照常触发 Ping。
     * 这样它不会再压住礼物按钮等首页控件。
     */
    @SuppressLint("ClickableViewAccessibility")
    private void makePingDraggable() {
        final int slop = ViewConfiguration.get(fabUp.getContext()).getScaledTouchSlop();
        fabUp.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private float startTx;
            private float startTy;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startTx = v.getTranslationX();
                        startTy = v.getTranslationY();
                        moved = false;
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (!moved && Math.hypot(dx, dy) > slop) {
                            moved = true;
                        }
                        if (moved) {
                            v.setTranslationX(startTx + dx);
                            v.setTranslationY(startTy + dy);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            savePingPosition(v);
                        } else {
                            v.performClick();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void savePingPosition(View v) {
        Context context = getActivity();
        if (context == null) {
            return;
        }
        PreferenceUtils.setPrefString(context, KEY_PING_X, String.valueOf(v.getTranslationX()));
        PreferenceUtils.setPrefString(context, KEY_PING_Y, String.valueOf(v.getTranslationY()));
    }
    @Override
    public void onClickFab(View view) {
//        super.onClickFab(view);
        EventBusUtil.getEventBus().post(new PingEvent());
    }
}
