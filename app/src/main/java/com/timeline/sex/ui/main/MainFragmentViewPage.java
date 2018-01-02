package com.timeline.sex.ui.main;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.sspacee.common.util.DoubleClickExit;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PermissionHelper;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.sex.R;
import com.timeline.sex.constant.Constants;
import com.timeline.sex.data.UserLoginUtil;
import com.timeline.sex.data.config.ConfigActionJump;
import com.timeline.sex.data.config.LogAddTofile;
import com.timeline.sex.data.config.TabChangeEvent;
import com.timeline.sex.service.LogUploadService;
import com.timeline.sex.ui.base.app.BaseDrawerActivity;
import com.timeline.sex.ui.inte.OnBackKeyDownListener;
import com.timeline.sex.ui.maintab.TabCustomeFragment;
import com.timeline.sex.ui.maintab.TabVipFragment;
import com.timeline.sex.ui.maintab.TabVpnFragment;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import co.mobiwise.materialintro.animation.MaterialIntroListener;
import co.mobiwise.materialintro.shape.Focus;
import co.mobiwise.materialintro.shape.FocusGravity;
import co.mobiwise.materialintro.view.MaterialIntroView;

/**
 * Created by themass on 2016/3/1.
 */
public class MainFragmentViewPage extends BaseDrawerActivity implements ActivityCompat.OnRequestPermissionsResultCallback,MaterialIntroListener {
    public List<ItemFragment> list = new ArrayList<>();
    public boolean init = false;
    private Set<OnBackKeyDownListener> keyListeners = new HashSet<>();
    private ConfigActionJump jump = new ConfigActionJump();
    private LogAddTofile logAdd = new LogAddTofile();
    private TabLayout mTabLayout;
    private ViewPager mViewPager;
    private MyPagerAdapter myPagerAdapter;
    private String POSITION = "POSITION";
    private int index = 0;
    private static final String SETTING_TAG="SETTING_TAG";
    private static final String WITER_TAG="WITER_TAG";
    //权限检测类
    private PermissionHelper mPermissionHelper;
    private MaterialIntroView materialIntroView;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main_viewpage);
//        startService(CharonVpnService.class);
//        startService(CharonVpnService.class);
        EventBusUtil.getEventBus().register(jump);
        EventBusUtil.getEventBus().register(logAdd);
        mPermissionHelper = new PermissionHelper(this);
//        mPermissionHelper.checkNeedPermissions();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TabChangeEvent event) {
        initTabs();
        LogUtil.i("onEvent:initTabs");
//        myPagerAdapter.notifyDataSetChanged();
    }

    public void addListener(OnBackKeyDownListener keyListener) {
        keyListeners.add(keyListener);
    }

    public void removeListener(OnBackKeyDownListener keyListener) {
        keyListeners.remove(keyListener);
    }

    public void setupView() {
        mTabLayout = (TabLayout) findViewById(R.id.tabs);
        mViewPager = (ViewPager) findViewById(R.id.vp_view);
        initTabs();
        if(!UserLoginUtil.isVIP2())
            AdsManager.getInstans().showInterstitialAds(this, AdsContext.Categrey.CATEGREY_VPN1,false);
    }
    public void showSetting(){
        showHit(getNaviButton(),FocusGravity.RIGHT,R.string.hidden_area_hit,SETTING_TAG);
    }
    public void showWither(){
        if(ivWeather!=null)
            showHit(ivWeather,FocusGravity.RIGHT,R.string.wither_hit,WITER_TAG);
    }
    public void showHit(View view, FocusGravity gravity,int hitsId,String tag){
        materialIntroView = new MaterialIntroView.Builder(MainFragmentViewPage.this)
                .enableDotAnimation(true)
                .setFocusGravity(gravity)
                .setFocusType(Focus.MINIMUM)
                .setDelayMillis(100)
                .enableFadeAnimation(true)
                .setInfoTextSize(18)
                .performClick(true)
                .setIdempotent(true)
                .setInfoText(getString(hitsId))
                .setTarget(view)
                .setListener(MainFragmentViewPage.this)
                .setUsageId(tag)
                .show();
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (grantResults != null) {
            for (int ret : grantResults) {
                if (ret != PackageManager.PERMISSION_GRANTED) {
                    finish();
                }
            }
        }
        mPermissionHelper.checkNeedPermissions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mPermissionHelper.checkNeedPermissions();
    }

    private void initTabs() {
        list.clear();
        LayoutInflater inflater = LayoutInflater.from(this);
        addData(inflater, R.string.tab_tag_index, TabVpnFragment.class,
                R.drawable.ac_bg_tab_index, R.string.tab_index, null, 1);
        if (PreferenceUtils.getPrefBoolean(this, Constants.AREA_SWITCH, true)) {
            addData(inflater, R.string.tab_tag_vip, TabVipFragment.class,
                    R.drawable.ac_bg_tab_index, R.string.tab_vip, null, 2);
        }
        addData(inflater, R.string.tab_tag_customer, TabCustomeFragment.class,
                R.drawable.ac_bg_tab_index, R.string.tab_customer, null, 3);

        myPagerAdapter = new MyPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(myPagerAdapter);
        mTabLayout.setupWithViewPager(mViewPager);//将TabLayout和ViewPager关联起来。
        mTabLayout.setOnTabSelectedListener(new ViewPagerOnTabSelectedListener(mViewPager));
        for (int i = 0; i < mTabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = mTabLayout.getTabAt(i);
            tab.setCustomView(myPagerAdapter.getTabView(i, (i == 0)));
        }
    }

    private void addData(LayoutInflater inflater, int tag, Class<? extends Fragment> clss,
                         int icon, int title, Bundle args, int abslIndex) {
        list.add(new ItemFragment(tag, clss, icon, title, args, abslIndex));

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(POSITION, mTabLayout.getSelectedTabPosition());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mViewPager.setCurrentItem(savedInstanceState.getInt(POSITION));
    }

    @Override
    public void onDestroy() {
        LogUtil.i("main destory");
//        stopService(CharonVpnService.class);
        stopService(LogUploadService.class);
        EventBusUtil.getEventBus().unregister(jump);
        EventBusUtil.getEventBus().unregister(logAdd);
        super.onDestroy();
        MobAgent.killProcess(this);
        System.exit(0);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtil.i("onKeyUp");
        if (closeDrawer()) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if(materialIntroView!=null && materialIntroView.isShown()){
//                ToastUtil.showShort(R.string.close_hit);
                materialIntroView.dismiss();
                return true;
            }
            boolean flag = false;
            for (OnBackKeyDownListener l : keyListeners) {
                flag = flag || l.onkeyBackDown();
            }
            if (flag) {
                return true;
            }
//            if (CharonVpnService.VPN_STATUS_NOTIF) {
//                moveTaskToBack(true);
//                return true;
//            }

            if (!DoubleClickExit.check()) {
                ToastUtil.showShort(getString(R.string.close_over));
                return true;
            } else {
                finish();
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    @Override
    public void onUserClicked(String materialIntroViewId) {
        LogUtil.i(materialIntroViewId+"--click");
    }
    public class ViewPagerOnTabSelectedListener implements TabLayout.OnTabSelectedListener {
        private final ViewPager mViewPager;

        public ViewPagerOnTabSelectedListener(ViewPager viewPager) {
            mViewPager = viewPager;
        }

        @Override
        public void onTabSelected(TabLayout.Tab tab) {
            LogUtil.i("tab select:" + tab.getPosition());
            mViewPager.setCurrentItem(tab.getPosition());
            setToolbarTitle(getString(list.get(tab.getPosition()).title), false);
            index = tab.getPosition();
             if(list.get(tab.getPosition()).abslIndex==2 ){
                showSetting();
             }
            if(list.get(tab.getPosition()).abslIndex==1){
                showWither();
            }
        }

        @Override
        public void onTabUnselected(TabLayout.Tab tab) {

        }

        @Override
        public void onTabReselected(TabLayout.Tab tab) {
            // No-op
        }
    }

    //ViewPager适配器
    class MyPagerAdapter extends FragmentPagerAdapter {

        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public long getItemId(int position) {
            return list.get(position).abslIndex;
        }

        @Override
        public Fragment getItem(int position) {
            try {
                return list.get(position).clss.newInstance();
            } catch (Exception e) {
                LogUtil.e(e);
                return null;
            }
        }

        public View getTabView(int position, boolean selected) {
            View indicator = LayoutInflater.from(MainFragmentViewPage.this).inflate(R.layout.tab_widiget, null);
            ImageView imgView = (ImageView) indicator.findViewById(R.id.navi_icon);
            TextView titleView = (TextView) indicator.findViewById(R.id.navi_title);
            imgView.setImageResource(list.get(position).icon);
            titleView.setText(list.get(position).title);
            if (selected) {
                indicator.setSelected(true);
            }
            return indicator;
        }
    }

    class ItemFragment {
        public int tag;
        public Class<? extends Fragment> clss;
        public int icon;
        public int title;
        public Bundle args;
        public int abslIndex;

        public ItemFragment(int tag, Class<? extends Fragment> clss, int icon, int title, Bundle args, int abslIndex) {
            this.tag = tag;
            this.clss = clss;
            this.icon = icon;
            this.title = title;
            this.args = args;
            this.abslIndex = abslIndex;

        }
    }
}
